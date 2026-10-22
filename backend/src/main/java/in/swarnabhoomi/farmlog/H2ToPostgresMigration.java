package in.swarnabhoomi.farmlog;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("migration")
public class H2ToPostgresMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(H2ToPostgresMigration.class);
    private static final List<String> TABLES = List.of("farm_entry", "farm_reminder", "farm_photo",
            "farm_product_listing", "organic_consultant");

    private final JdbcTemplate target;
    private final FarmPhotoRepository photoRepository;
    private final PhotoObjectStorage photoStorage;

    @Value("${H2_FILE_PATH:./data/swarnabhoomi}") private String h2FilePath;
    @Value("${H2_USER:sa}") private String h2Username;
    @Value("${H2_PASSWORD:}") private String h2Password;

    public H2ToPostgresMigration(JdbcTemplate target, FarmPhotoRepository photoRepository,
            PhotoObjectStorage photoStorage) {
        this.target = target;
        this.photoRepository = photoRepository;
        this.photoStorage = photoStorage;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String databaseProduct = target.queryForObject("SELECT version()", String.class);
        if (databaseProduct == null || !databaseProduct.toLowerCase(Locale.ROOT).contains("postgresql")) {
            throw new IllegalStateException("The migration target must be PostgreSQL.");
        }
        if (!photoStorage.isS3Enabled()) {
            throw new IllegalStateException("Configure S3_BUCKET and AWS credentials before migration.");
        }

        String sourceUrl = "jdbc:h2:file:" + h2FilePath + ";DB_CLOSE_DELAY=-1";
        try (Connection source = DriverManager.getConnection(sourceUrl, h2Username, h2Password)) {
            for (String table : TABLES) {
                if (("farm_product_listing".equals(table) || "organic_consultant".equals(table))
                        && !tableExists(source.getMetaData(), table)) {
                    log.info("Skipping optional table {}; it does not exist in the source H2 database.", table);
                    continue;
                }
                int copied = copyTable(source, table);
                log.info("Migrated {} rows from H2 table {}", copied, table);
            }
        }

        int migratedPhotos = 0;
        for (FarmPhoto photo : photoRepository.findAll()) {
            if (photo.getS3Key() == null || photo.getS3Key().isBlank()) {
                photoStorage.migrateToS3(photo);
                photoRepository.saveAndFlush(photo);
                migratedPhotos++;
            }
        }
        resetEntrySequence();
        resetListingSequence();
        resetConsultantSequence();
        log.info("H2 migration completed; moved {} photo objects to S3. Keep the original H2 volume as a backup.",
                migratedPhotos);
    }

    private int copyTable(Connection source, String table) throws SQLException {
        Set<String> sourceColumns = columns(source.getMetaData(), table);
        Set<String> targetColumns = targetColumns(table);
        List<String> commonColumns = new ArrayList<>();
        for (String column : sourceColumns) {
            if (targetColumns.contains(column)) commonColumns.add(column);
        }
        if (commonColumns.isEmpty() || !commonColumns.contains("id")) {
            throw new IllegalStateException("No compatible ID column found for table " + table + ".");
        }

        String columnList = String.join(", ", commonColumns);
        String placeholders = String.join(", ", commonColumns.stream().map(column -> "?").toList());
        String insert = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders
                + ") ON CONFLICT DO NOTHING";
        int copied = 0;
        try (PreparedStatement select = source.prepareStatement("SELECT * FROM " + table);
                ResultSet rows = select.executeQuery();
                Connection connection = target.getDataSource().getConnection();
                PreparedStatement insertRow = connection.prepareStatement(insert)) {
            connection.setAutoCommit(false);
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                for (int index = 0; index < commonColumns.size(); index++) {
                    String column = commonColumns.get(index);
                    int sourceIndex = findColumn(metadata, column);
                    if (sourceIndex < 1) {
                        throw new IllegalStateException("Source table " + table
                                + " is missing column " + column + ".");
                    }
                    if ("photo_data".equals(column)) {
                        insertRow.setBytes(index + 1, rows.getBytes(sourceIndex));
                    } else {
                        insertRow.setObject(index + 1, rows.getObject(sourceIndex));
                    }
                }
                copied += insertRow.executeUpdate();
            }
            connection.commit();
        }
        return copied;
    }

    private Set<String> columns(DatabaseMetaData metadata, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet columns = metadata.getColumns(null, null, table.toUpperCase(Locale.ROOT), null)) {
            while (columns.next()) result.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        if (result.isEmpty()) {
            try (ResultSet columns = metadata.getColumns(null, null, table, null)) {
                while (columns.next()) result.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Required H2 table " + table + " does not exist.");
        }
        return result;
    }

    private boolean tableExists(DatabaseMetaData metadata, String table) throws SQLException {
        for (String candidate : List.of(table.toUpperCase(Locale.ROOT), table)) {
            try (ResultSet tables = metadata.getTables(null, null, candidate, new String[]{"TABLE"})) {
                if (tables.next()) return true;
            }
        }
        return false;
    }

    private Set<String> targetColumns(String table) {
        return new HashSet<>(target.query(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
                (rows, index) -> rows.getString(1).toLowerCase(Locale.ROOT), table));
    }

    private int findColumn(ResultSetMetaData metadata, String expected) throws SQLException {
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            if (metadata.getColumnLabel(index).equalsIgnoreCase(expected)) return index;
        }
        return -1;
    }

    private void resetEntrySequence() {
        target.execute("SELECT setval(pg_get_serial_sequence('farm_entry', 'id'), "
                + "COALESCE((SELECT MAX(id) FROM farm_entry), 0) + 1, false)");
    }

    private void resetListingSequence() {
        target.execute("SELECT setval(pg_get_serial_sequence('farm_product_listing', 'id'), "
                + "COALESCE((SELECT MAX(id) FROM farm_product_listing), 0) + 1, false)");
    }

    private void resetConsultantSequence() {
        target.execute("SELECT setval(pg_get_serial_sequence('organic_consultant', 'id'), "
                + "COALESCE((SELECT MAX(id) FROM organic_consultant), 0) + 1, false)");
    }
}
