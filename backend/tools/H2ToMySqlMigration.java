import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Offline migration of a verified H2 logical snapshot into an EMPTY MySQL schema. */
public class H2ToMySqlMigration {
    record Column(String source, String name, int type, String typeName, int size, int scale,
                  boolean nullable, boolean identity, long identityBase, String defaultValue, boolean ascii) {}
    record Key(String name, boolean unique, List<String> columns) {}
    record ForeignKey(String name, String parent, List<String> childColumns, List<String> parentColumns, int updateRule, int deleteRule) {}
    record Check(String name, String clause) {}
    record Table(String source, String name, List<Column> columns, List<String> primaryKey, List<Key> indexes, List<ForeignKey> foreignKeys, List<Check> checks) {}
    record Digest(long rows, String sha256) {}
    static final ObjectMapper JSON = new ObjectMapper();
    static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("plan", "apply", "verify").contains(args[0])) {
            throw new IllegalArgumentException("Usage: H2ToMySqlMigration.java plan|apply|verify snapshot.sql private-report-directory");
        }
        Path directory = Path.of(args[2]).toAbsolutePath().normalize(); Files.createDirectories(directory);
        try { run(args[0], Path.of(args[1]).toAbsolutePath().normalize(), directory); }
        catch (Exception error) {
            Path report = directory.resolve("migration-failure.txt");
            try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(report))) { error.printStackTrace(output); }
            System.err.println("Migration stopped: " + error.getClass().getSimpleName() + ". Private diagnostic: " + report);
            System.exit(1);
        }
    }

    static void run(String mode, Path script, Path directory) throws Exception {
        Class.forName("org.h2.Driver"); Class.forName("com.mysql.cj.jdbc.Driver");
        if (!Files.isRegularFile(script)) throw new IllegalArgumentException("Source snapshot does not exist.");
        String scriptHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(script)));
        Path restored = directory.resolve("restored-h2/snapshot"); Files.createDirectories(restored.getParent());
        Path restoredFile = Path.of(restored + ".mv.db"), marker = directory.resolve("restored-source-sha256.txt");
        String sourceUrl = "jdbc:h2:file:" + restored.toString().replace('\\', '/') + ";DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE";
        if (!Files.exists(restoredFile)) {
            try (Connection source = DriverManager.getConnection(sourceUrl, "sa", ""); Statement sql = source.createStatement()) {
                sql.execute("RUNSCRIPT FROM " + literal(script.toString().replace('\\', '/')) + " CHARSET 'UTF-8'");
            }
            Files.writeString(marker, scriptHash);
        } else if (!Files.isRegularFile(marker) || !Files.readString(marker).trim().equals(scriptHash)) {
            throw new IllegalStateException("Existing restored H2 file does not match this snapshot.");
        }
        try (Connection source = DriverManager.getConnection(sourceUrl + ";IFEXISTS=TRUE", "sa", "")) {
            List<Table> tables = readSchema(source);
            if (tables.isEmpty()) throw new IllegalStateException("Source snapshot has no application tables.");
            Map<String, Digest> sourceDigests = new TreeMap<>();
            for (Table table : tables) sourceDigests.put(table.name(), digest(source, table, true));
            Path manifest = script.getParent().resolve("manifest.json");
            if (Files.isRegularFile(manifest)) {
                var expected = JSON.readTree(manifest.toFile()).path("tables");
                if (expected.size() != tables.size()) throw new IllegalStateException("Snapshot table inventory does not match export manifest.");
                for (Table table : tables) if (expected.path(table.name()).asLong(-1) != sourceDigests.get(table.name()).rows())
                    throw new IllegalStateException("Snapshot row count differs from export manifest for " + table.name());
            }
            String ddl = tables.stream().map(H2ToMySqlMigration::createTable).collect(Collectors.joining(";\n\n")) + ";\n";
            Files.writeString(directory.resolve("mysql-schema-plan.sql"), ddl);
            JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("migration-plan.json").toFile(), Map.of(
                    "sourceSqlSha256", scriptHash, "tableCount", tables.size(), "sourceDigests", sourceDigests, "schema", tables));
            if (mode.equals("plan")) {
                System.out.println(JSON.writeValueAsString(Map.of("planReady", true, "tables", tables.size(), "rows", sourceDigests.values().stream().mapToLong(Digest::rows).sum(), "reportDirectory", directory.toString())));
                return;
            }
            String url = required("MIGRATION_MYSQL_URL"), user = required("MIGRATION_MYSQL_USER"), password = required("MIGRATION_MYSQL_PASSWORD");
            String expectedSchema = System.getenv().getOrDefault("MIGRATION_MYSQL_SCHEMA", "swarnbhoomi");
            try (Connection target = DriverManager.getConnection(url, user, password)) {
                if (!"MySQL".equalsIgnoreCase(target.getMetaData().getDatabaseProductName()) || !expectedSchema.equals(target.getCatalog()))
                    throw new IllegalStateException("Target must be the explicitly selected MySQL schema.");
                execute(target, "SET SESSION time_zone='+00:00'");
                if (mode.equals("verify")) {
                    for (Table table : tables) if (!digest(target, table, false).equals(sourceDigests.get(table.name())))
                        throw new IllegalStateException("Post-startup row verification failed for " + table.name());
                    long expectedForeignKeys = tables.stream().mapToLong(table -> table.foreignKeys().size()).sum();
                    long actualForeignKeys;
                    try (Statement sql = target.createStatement(); ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()")) {
                        rows.next(); actualForeignKeys = rows.getLong(1);
                    }
                    if (expectedForeignKeys != actualForeignKeys) throw new IllegalStateException("Foreign key inventory does not match source.");
                    Map<String,Object> result = Map.of("allRowsVerified", true, "verifiedAt", Instant.now().toString(), "targetSchema", expectedSchema,
                            "tables", sourceDigests, "tableCount", tables.size(), "totalRows", sourceDigests.values().stream().mapToLong(Digest::rows).sum(), "foreignKeys", actualForeignKeys);
                    JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("mysql-post-startup-verification.json").toFile(), result);
                    System.out.println(JSON.writeValueAsString(Map.of("allRowsVerified", true, "tables", tables.size(), "rows", sourceDigests.values().stream().mapToLong(Digest::rows).sum(), "foreignKeys", actualForeignKeys)));
                    return;
                }
                try (ResultSet existing = target.getMetaData().getTables(target.getCatalog(), null, "%", new String[]{"TABLE"})) {
                    if (existing.next()) throw new IllegalStateException("Target schema is not empty. No existing records will be replaced.");
                }
                execute(target, "SET SESSION sql_mode=CONCAT(@@sql_mode,',NO_AUTO_VALUE_ON_ZERO,NO_BACKSLASH_ESCAPES')");
                List<String> created = new ArrayList<>();
                for (Table table : tables) {
                    execute(target, createTable(table)); created.add(table.name());
                    JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("created-tables.json").toFile(), created);
                }
                target.setAutoCommit(false);
                Map<String, Digest> verified = new TreeMap<>();
                try {
                    for (Table table : tables) copyRows(source, target, table);
                    for (Table table : tables) {
                        Digest actual = digest(target, table, false);
                        if (!actual.equals(sourceDigests.get(table.name()))) throw new IllegalStateException("Full row verification failed for " + table.name());
                        verified.put(table.name(), actual);
                    }
                    target.commit();
                } catch (Exception error) { target.rollback(); throw error; }
                target.setAutoCommit(true);
                int foreignKeys = 0;
                for (Table table : tables) {
                    for (Key index : table.indexes()) {
                        if (index.unique() || index.columns().equals(table.primaryKey())) continue;
                        execute(target, "CREATE INDEX " + q(shortName(index.name())) + " ON " + q(table.name()) + " (" + indexColumns(table, index, false) + ")");
                    }
                }
                for (Table table : tables) for (ForeignKey fk : table.foreignKeys()) {
                    execute(target, "ALTER TABLE " + q(table.name()) + " ADD CONSTRAINT " + q(shortName(fk.name()))
                            + " FOREIGN KEY (" + quoted(fk.childColumns()) + ") REFERENCES " + q(fk.parent()) + " (" + quoted(fk.parentColumns()) + ")"
                            + " ON UPDATE " + rule(fk.updateRule()) + " ON DELETE " + rule(fk.deleteRule()));
                    foreignKeys++;
                }
                Map<String, Object> report = new LinkedHashMap<>(); report.put("verifiedAt", Instant.now().toString());
                report.put("targetSchema", expectedSchema); report.put("sourceSqlSha256", scriptHash);
                report.put("tables", verified); report.put("tableCount", tables.size()); report.put("foreignKeys", foreignKeys);
                report.put("totalRows", verified.values().stream().mapToLong(Digest::rows).sum()); report.put("allRowsVerified", true);
                JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("mysql-migration-verification.json").toFile(), report);
                System.out.println(JSON.writeValueAsString(report));
            }
        }
    }

    static List<Table> readSchema(Connection source) throws Exception {
        List<Table> result = new ArrayList<>(); DatabaseMetaData meta = source.getMetaData();
        try (ResultSet tables = meta.getTables(null, "PUBLIC", "%", new String[]{"TABLE", "BASE TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME"); List<Column> columns = new ArrayList<>();
                try (ResultSet rows = meta.getColumns(null, "PUBLIC", name, "%")) {
                    while (rows.next()) {
                        String column = rows.getString("COLUMN_NAME"); boolean identity = "YES".equals(rows.getString("IS_AUTOINCREMENT"));
                        long base = 1;
                        if (identity) try (PreparedStatement query = source.prepareStatement("SELECT IDENTITY_BASE,IDENTITY_INCREMENT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME=? AND COLUMN_NAME=?")) {
                            query.setString(1, name); query.setString(2, column); try (ResultSet value = query.executeQuery()) {
                                value.next(); base = value.getLong(1); if (value.getLong(2) != 1) throw new IllegalStateException("Non-unit identity increment is not supported.");
                            }
                        }
                        boolean ascii = name.equalsIgnoreCase("browser_push_subscription") && column.equalsIgnoreCase("endpoint");
                        if (ascii) try (Statement query = source.createStatement(); ResultSet values = query.executeQuery("SELECT " + hq(column) + " FROM \"PUBLIC\"." + hq(name))) {
                            while (values.next()) { String value = values.getString(1); if (value != null && value.chars().anyMatch(ch -> ch > 127)) throw new IllegalStateException("Push endpoints require explicit Unicode index mapping."); }
                        }
                        columns.add(new Column(column, lower(column), rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME"), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"), rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls, identity, base, rows.getString("COLUMN_DEF"), ascii));
                    }
                }
                SortedMap<Short,String> pk = new TreeMap<>();
                try (ResultSet rows = meta.getPrimaryKeys(null, "PUBLIC", name)) { while (rows.next()) pk.put(rows.getShort("KEY_SEQ"), lower(rows.getString("COLUMN_NAME"))); }
                Map<String, SortedMap<Short,String>> indexColumns = new TreeMap<>(); Map<String,Boolean> unique = new HashMap<>();
                try (ResultSet rows = meta.getIndexInfo(null, "PUBLIC", name, false, false)) { while (rows.next()) {
                    String index = rows.getString("INDEX_NAME"), column = rows.getString("COLUMN_NAME"); if (index == null || column == null) continue;
                    indexColumns.computeIfAbsent(index, key -> new TreeMap<>()).put(rows.getShort("ORDINAL_POSITION"), lower(column)); unique.put(index, !rows.getBoolean("NON_UNIQUE"));
                } }
                List<Key> indexes = indexColumns.entrySet().stream().map(entry -> new Key(entry.getKey(), unique.get(entry.getKey()), List.copyOf(entry.getValue().values()))).toList();
                Map<String, SortedMap<Short,String>> child = new TreeMap<>(), parent = new TreeMap<>(); Map<String,String> parentTable = new HashMap<>(); Map<String,Integer> updates = new HashMap<>(), deletes = new HashMap<>();
                try (ResultSet rows = meta.getImportedKeys(null, "PUBLIC", name)) { while (rows.next()) {
                    String fk = rows.getString("FK_NAME"); short order = rows.getShort("KEY_SEQ");
                    child.computeIfAbsent(fk, key -> new TreeMap<>()).put(order, lower(rows.getString("FKCOLUMN_NAME")));
                    parent.computeIfAbsent(fk, key -> new TreeMap<>()).put(order, lower(rows.getString("PKCOLUMN_NAME")));
                    parentTable.put(fk, lower(rows.getString("PKTABLE_NAME"))); updates.put(fk, rows.getInt("UPDATE_RULE")); deletes.put(fk, rows.getInt("DELETE_RULE"));
                } }
                List<ForeignKey> foreignKeys = child.entrySet().stream().map(entry -> new ForeignKey(entry.getKey(), parentTable.get(entry.getKey()), List.copyOf(entry.getValue().values()), List.copyOf(parent.get(entry.getKey()).values()), updates.get(entry.getKey()), deletes.get(entry.getKey()))).toList();
                List<Check> checks = new ArrayList<>();
                try (PreparedStatement query = source.prepareStatement("SELECT C.CONSTRAINT_NAME,C.CHECK_CLAUSE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS T JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS C ON T.CONSTRAINT_SCHEMA=C.CONSTRAINT_SCHEMA AND T.CONSTRAINT_NAME=C.CONSTRAINT_NAME WHERE T.TABLE_SCHEMA='PUBLIC' AND T.TABLE_NAME=? AND T.CONSTRAINT_TYPE='CHECK'")) {
                    query.setString(1, name); try (ResultSet rows = query.executeQuery()) { while (rows.next()) checks.add(new Check(rows.getString(1), rows.getString(2))); }
                }
                result.add(new Table(name, lower(name), columns, List.copyOf(pk.values()), indexes, foreignKeys, checks));
            }
        }
        result.sort(Comparator.comparing(Table::name)); return result;
    }

    static String createTable(Table table) {
        List<String> definitions = new ArrayList<>();
        for (Column c : table.columns()) {
            String type = mysqlType(c), value = q(c.name()) + " " + type + (c.nullable() ? " NULL" : " NOT NULL");
            if (c.identity()) value += " AUTO_INCREMENT";
            else if (c.defaultValue() != null && !c.defaultValue().equalsIgnoreCase("NULL")) {
                String def = c.defaultValue(); if (def.equalsIgnoreCase("TRUE")) def = "1"; else if (def.equalsIgnoreCase("FALSE")) def = "0";
                if (!def.matches("[-+]?\\d+(?:\\.\\d+)?") && !def.matches("'(?s:.*)'") && !def.matches("(?i)CURRENT_TIMESTAMP(?:\\(\\d*\\))?")) throw new IllegalStateException("Default requires explicit translation for " + table.name() + "." + c.name());
                value += " DEFAULT " + (type.contains("TEXT") ? "(" + def + ")" : def);
            }
            definitions.add(value);
        }
        if (!table.primaryKey().isEmpty()) definitions.add("PRIMARY KEY (" + quoted(table.primaryKey()) + ")");
        for (Key index : table.indexes()) if (index.unique() && !index.columns().equals(table.primaryKey())) definitions.add("UNIQUE KEY " + q(shortName(index.name())) + " (" + indexColumns(table, index, true) + ")");
        for (Check check : table.checks()) definitions.add("CONSTRAINT " + q(shortName(check.name())) + " CHECK (" + check.clause().replaceAll("\"([A-Za-z0-9_]+)\"", "`$1`") + ")");
        long next = table.columns().stream().filter(Column::identity).mapToLong(Column::identityBase).max().orElse(1);
        return "CREATE TABLE " + q(table.name()) + " (\n  " + String.join(",\n  ", definitions) + "\n) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin" + (next > 1 ? " AUTO_INCREMENT=" + next : "");
    }

    static String mysqlType(Column c) {
        return switch (c.type()) {
            case Types.BIGINT -> "BIGINT"; case Types.INTEGER -> "INT"; case Types.SMALLINT -> "SMALLINT"; case Types.TINYINT -> "TINYINT";
            case Types.BOOLEAN, Types.BIT -> "BIT(1)";
            case Types.DOUBLE, Types.FLOAT -> "DOUBLE"; case Types.REAL -> "FLOAT";
            case Types.DECIMAL, Types.NUMERIC -> { if (c.size() > 65 || c.scale() > 30) throw new IllegalStateException("Decimal precision exceeds MySQL limits: " + c.name()); yield "DECIMAL(" + c.size() + "," + c.scale() + ")"; }
            case Types.CHAR, Types.NCHAR -> "CHAR(" + c.size() + ")";
            case Types.VARCHAR, Types.NVARCHAR -> c.ascii() ? "VARCHAR(" + c.size() + ") CHARACTER SET ascii COLLATE ascii_bin" : c.size() <= 255 ? "VARCHAR(" + c.size() + ")" : "LONGTEXT";
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR -> "LONGTEXT";
            case Types.BINARY, Types.VARBINARY -> c.size() <= 255 ? "VARBINARY(" + c.size() + ")" : "LONGBLOB";
            case Types.BLOB, Types.LONGVARBINARY -> "LONGBLOB";
            case Types.DATE -> "DATE"; case Types.TIME -> "TIME(6)"; case Types.TIMESTAMP -> "DATETIME(6)"; case Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP(6)";
            case Types.OTHER -> { if (c.typeName().startsWith("ENUM(")) yield c.typeName(); if (c.typeName().equalsIgnoreCase("UUID")) yield "CHAR(36)"; if (c.typeName().equalsIgnoreCase("JSON")) yield "LONGTEXT"; throw new IllegalStateException("Unsupported H2 type " + c.typeName() + " for " + c.name()); }
            default -> throw new IllegalStateException("Unsupported H2 type " + c.typeName() + " for " + c.name());
        };
    }

    static String indexColumns(Table table, Key index, boolean unique) {
        List<String> result = new ArrayList<>(); int bytes = 0;
        for (String name : index.columns()) {
            Column c = table.columns().stream().filter(column -> column.name().equals(name)).findFirst().orElseThrow(); String type = mysqlType(c);
            if (type.contains("TEXT") || type.contains("BLOB")) { if (unique) throw new IllegalStateException("Unique text index needs an explicit lossless mapping: " + table.name() + "." + name); result.add(q(name) + "(191)"); bytes += 764; }
            else { result.add(q(name)); bytes += c.ascii() ? c.size() : (c.type() == Types.VARCHAR || c.type() == Types.NVARCHAR || c.type() == Types.CHAR || c.type() == Types.NCHAR) ? c.size() * 4 : 8; }
        }
        if (bytes > 3072) throw new IllegalStateException("Index exceeds MySQL's key length limit: " + table.name() + "." + index.name());
        return String.join(",", result);
    }

    static void copyRows(Connection source, Connection target, Table table) throws Exception {
        String fields = table.columns().stream().map(c -> q(c.name())).collect(Collectors.joining(","));
        String parameters = String.join(",", Collections.nCopies(table.columns().size(), "?"));
        try (Statement query = source.createStatement(); ResultSet rows = query.executeQuery("SELECT " + table.columns().stream().map(c -> hq(c.source())).collect(Collectors.joining(",")) + " FROM \"PUBLIC\"." + hq(table.source())); PreparedStatement insert = target.prepareStatement("INSERT INTO " + q(table.name()) + " (" + fields + ") VALUES (" + parameters + ")")) {
            while (rows.next()) { for (int i = 0; i < table.columns().size(); i++) { Object value = value(rows, i + 1, table.columns().get(i), true); if (value instanceof byte[] bytes) insert.setBytes(i + 1, bytes); else if (value instanceof Instant instant) insert.setTimestamp(i + 1, Timestamp.from(instant), UTC); else insert.setObject(i + 1, value); } insert.executeUpdate(); }
        }
    }

    static Digest digest(Connection connection, Table table, boolean h2) throws Exception {
        List<String> rowHashes = new ArrayList<>();
        String fields = table.columns().stream().map(c -> h2 ? hq(c.source()) : q(c.name())).collect(Collectors.joining(","));
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT " + fields + " FROM " + (h2 ? "\"PUBLIC\"." + hq(table.source()) : q(table.name())))) {
            while (rows.next()) {
                MessageDigest row = MessageDigest.getInstance("SHA-256");
                for (int i = 0; i < table.columns().size(); i++) {
                    Object value = value(rows, i + 1, table.columns().get(i), h2);
                    if (value == null) { row.update((byte) 0); continue; }
                    row.update((byte) 1); byte[] bytes;
                    if (value instanceof byte[] binary) bytes = binary;
                    else if (value instanceof BigDecimal number) bytes = number.stripTrailingZeros().toPlainString().getBytes(StandardCharsets.UTF_8);
                    else if (value instanceof Double number) bytes = Double.toHexString(number).getBytes(StandardCharsets.UTF_8);
                    else bytes = value.toString().getBytes(StandardCharsets.UTF_8);
                    row.update(ByteBuffer.allocate(8).putLong(bytes.length).array()); row.update(bytes);
                }
                rowHashes.add(HexFormat.of().formatHex(row.digest()));
            }
        }
        Collections.sort(rowHashes); MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String hash : rowHashes) digest.update((hash + "\n").getBytes(StandardCharsets.US_ASCII));
        return new Digest(rowHashes.size(), HexFormat.of().formatHex(digest.digest()));
    }

    static Object value(ResultSet row, int index, Column c, boolean source) throws Exception {
        if (row.getObject(index) == null) return null;
        return switch (c.type()) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> row.getBytes(index);
            case Types.BOOLEAN, Types.BIT -> row.getBoolean(index);
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> row.getLong(index);
            case Types.DECIMAL, Types.NUMERIC -> row.getBigDecimal(index);
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> row.getDouble(index);
            case Types.DATE -> row.getObject(index, LocalDate.class);
            case Types.TIME -> row.getObject(index, LocalTime.class);
            case Types.TIMESTAMP -> row.getObject(index, LocalDateTime.class);
            case Types.TIMESTAMP_WITH_TIMEZONE -> source ? row.getObject(index, OffsetDateTime.class).toInstant() : row.getTimestamp(index, UTC).toInstant();
            default -> row.getString(index);
        };
    }

    static String rule(int rule) { return switch (rule) { case DatabaseMetaData.importedKeyCascade -> "CASCADE"; case DatabaseMetaData.importedKeySetNull -> "SET NULL"; case DatabaseMetaData.importedKeyNoAction, DatabaseMetaData.importedKeyRestrict -> "RESTRICT"; default -> throw new IllegalStateException("Foreign-key action needs explicit MySQL translation."); }; }
    static void execute(Connection connection, String sql) throws SQLException { try (Statement statement = connection.createStatement()) { statement.execute(sql); } }
    static String lower(String value) { return value.toLowerCase(Locale.ROOT); }
    static String q(String name) { return "`" + name.replace("`", "``") + "`"; }
    static String hq(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    static String quoted(List<String> names) { return names.stream().map(H2ToMySqlMigration::q).collect(Collectors.joining(",")); }
    static String shortName(String name) { String result = lower(name); return result.length() <= 64 ? result : result.substring(0, 54) + "_" + Integer.toHexString(name.hashCode()); }
    static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
    static String required(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Set " + name + " in the private process environment."); return value; }
}
