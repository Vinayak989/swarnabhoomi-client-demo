package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit one-time cutover, before the HTTP server, schedulers, or repositories start. */
public class MySqlCutoverEnvironment implements EnvironmentPostProcessor, Ordered {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mode = environment.getProperty("APP_MYSQL_CUTOVER_MODE", "off");
        if (mode.equals("off")) return;
        if (!mode.equals("check") && !mode.equals("apply")) throw new IllegalStateException("Unknown MySQL cutover mode.");
        Path directory = null;
        try {
            var target = new H2ToMySqlMigration.Target(required(environment, "MYSQL_JDBC_URL"),
                    required(environment, "MYSQL_USER"), required(environment, "MYSQL_PASSWORD"),
                    environment.getProperty("MYSQL_SCHEMA", "swarnbhoomi"));
            if (!target.url().startsWith("jdbc:mysql:") || target.url().matches("(?is).*[?&](?:user|password)=.*"))
                throw new IllegalArgumentException("Use a MySQL JDBC URL with separate credential variables.");
            String runId = required(environment, "APP_MYSQL_CUTOVER_ID");
            if (!runId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid cutover ID.");
            Path root = Path.of(environment.getProperty("DB_BACKUP_DIRECTORY", "/var/data/backups")).toAbsolutePath().normalize();
            directory = root.resolve("mysql-cutover").resolve(runId);
            Path completion = directory.resolve("completion.json");
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (target.url() + "\n" + target.user() + "\n" + target.schema()).getBytes(StandardCharsets.UTF_8)));
            if (mode.equals("check")) {
                H2ToMySqlMigration.preflight(target, !Files.isRegularFile(completion));
                put(environment, Map.of("app.database.mysql-target-check", "ready"));
                return;
            }
            Files.createDirectories(directory);
            if (Files.isRegularFile(completion)) {
                var saved = JSON.readTree(completion.toFile());
                if (!fingerprint.equals(saved.path("targetFingerprint").asText()))
                    throw new IllegalStateException("Completed migration belongs to a different target.");
                H2ToMySqlMigration.preflight(target, false);
            } else {
                Path sourceInfo = directory.resolve("source-snapshot.json");
                Path verified = directory.resolve("mysql-migration-verification.json");
                if (!Files.isRegularFile(verified)) {
                    // Check credentials and empty schema before touching even the H2 backup directory.
                    H2ToMySqlMigration.preflight(target, true);
                    String sourceUrl = required(environment, "spring.datasource.url");
                    if (!sourceUrl.startsWith("jdbc:h2:file:")) throw new IllegalStateException("Cutover requires a file-based H2 source.");
                    if (!sourceUrl.matches("(?is).*;IFEXISTS=.*")) sourceUrl += ";IFEXISTS=TRUE";
                    var source = new DataSourceProperties(); source.setUrl(sourceUrl);
                    source.setUsername(environment.getProperty("spring.datasource.username", "sa"));
                    source.setPassword(environment.getProperty("spring.datasource.password", ""));
                    var dataSource = new DriverManagerDataSource(sourceUrl, source.getUsername(), source.getPassword());
                    var backup = new H2DatabaseSnapshotService(dataSource, source, JSON, root.toString()).create();
                    JSON.writerWithDefaultPrettyPrinter().writeValue(sourceInfo.toFile(), Map.of(
                            "archive", backup.archive().toString(), "sha256", backup.sha256(), "createdAt", backup.createdAt().toString()));
                    H2ToMySqlMigration.run("apply", backup.archive().getParent().resolve("h2-snapshot.sql"), directory, target);
                }
                var source = JSON.readTree(sourceInfo.toFile());
                Path archive = Path.of(source.path("archive").asText()).toAbsolutePath().normalize();
                if (!archive.startsWith(root) || !Files.isRegularFile(archive)) throw new IllegalStateException("Source backup is missing.");
                // Reopen MySQL and verify again before any application code can write to it.
                H2ToMySqlMigration.run("verify", archive.getParent().resolve("h2-snapshot.sql"), directory, target);
                var report = JSON.readTree(verified.toFile());
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("completedAt", Instant.now().toString()); result.put("targetFingerprint", fingerprint);
                result.put("source", source); result.put("tableCount", report.path("tableCount").asInt());
                result.put("totalRows", report.path("totalRows").asLong()); result.put("foreignKeys", report.path("foreignKeys").asInt());
                result.put("allRowsVerified", report.path("allRowsVerified").asBoolean());
                Path temporary = directory.resolve("completion.pending.json");
                JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), result);
                Files.move(temporary, completion, StandardCopyOption.ATOMIC_MOVE);
            }
            var result = JSON.readTree(completion.toFile());
            put(environment, Map.of(
                    "spring.datasource.url", target.url(), "spring.datasource.username", target.user(),
                    "spring.datasource.password", target.password(), "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver",
                    "spring.jpa.hibernate.ddl-auto", "validate", "spring.jpa.properties.hibernate.jdbc.time_zone", "UTC",
                    "app.database.backup.enabled", "false", "app.database.mysql-target-check", "verified",
                    "app.database.migrated-tables", result.path("tableCount").asText(),
                    "app.database.migrated-rows", result.path("totalRows").asText()));
            System.out.println("MySQL cutover verified: tables=" + result.path("tableCount").asInt()
                    + ", rows=" + result.path("totalRows").asLong() + "; original H2 and backups retained.");
        } catch (Exception failure) {
            if (mode.equals("check")) {
                String diagnostic = failure.getClass().getSimpleName();
                if (failure instanceof java.sql.SQLException sql) diagnostic += ":" + sql.getSQLState() + ":" + sql.getErrorCode();
                put(environment, Map.of("app.database.mysql-target-check", "failed", "app.database.mysql-target-error", diagnostic));
                return;
            }
            if (directory != null) try {
                Files.createDirectories(directory);
                try (var writer = new java.io.PrintWriter(Files.newBufferedWriter(directory.resolve("cutover-failure.txt")))) { failure.printStackTrace(writer); }
            } catch (Exception ignored) { }
            throw new IllegalStateException("MySQL cutover stopped before the app started. Original H2 retained. Check the private cutover report.");
        }
    }

    private static String required(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing migration setting: " + key);
        return value;
    }

    private static void put(ConfigurableEnvironment environment, Map<String,?> settings) {
        environment.getPropertySources().addFirst(new MapPropertySource("verified-mysql-cutover", new LinkedHashMap<>(settings)));
    }
}
