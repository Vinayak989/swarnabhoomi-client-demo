package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Service;

@Service
public class H2DatabaseSnapshotService {
    private final DataSource source;
    private final DataSourceProperties properties;
    private final ObjectMapper json;
    private final Path backupRoot;
    private Snapshot cached;

    public H2DatabaseSnapshotService(DataSource source, DataSourceProperties properties, ObjectMapper json,
            @Value("${DB_BACKUP_DIRECTORY:/var/data/backups}") String backupDirectory) {
        this.source = source; this.properties = properties; this.json = json;
        this.backupRoot = Path.of(backupDirectory).toAbsolutePath().normalize();
    }

    public synchronized Snapshot create() throws Exception {
        if (cached != null && Files.isRegularFile(cached.archive())) return cached;
        Instant createdAt = Instant.now();
        Path directory = backupRoot.resolve("h2-export-" + createdAt.toString().replaceAll("[^0-9TZ]", "") + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(directory);
        Path nativeBackup = directory.resolve("h2-native-backup.zip");
        String sourceUrl;
        try (Connection connection = source.getConnection()) {
            if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                throw new IllegalStateException("This export is only available for an H2 source database.");
            }
            sourceUrl = connection.getMetaData().getURL();
            String filePath = sourceUrl.replaceFirst("^jdbc:h2:file:", "").split(";", 2)[0];
            Path sourceFile = Path.of(filePath + ".mv.db").toAbsolutePath().normalize();
            if (Files.isRegularFile(sourceFile) && Files.getFileStore(directory).getUsableSpace() < Math.max(50_000_000L, Files.size(sourceFile) * 6)) {
                throw new IllegalStateException("Insufficient space for a verified database backup.");
            }
            try (var statement = connection.createStatement()) { statement.execute("BACKUP TO " + literal(nativeBackup)); }
        }
        Path restoreDirectory = directory.resolve("verified-snapshot");
        Files.createDirectories(restoreDirectory);
        Path database = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(nativeBackup))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (entry.isDirectory() || !entry.getName().endsWith(".mv.db")) continue;
                if (database != null) throw new IllegalStateException("Backup contains more than one H2 database.");
                Path destination = restoreDirectory.resolve(Path.of(entry.getName()).getFileName()).normalize();
                if (!destination.startsWith(restoreDirectory)) throw new IllegalStateException("Unsafe backup entry.");
                Files.copy(zip, destination);
                database = destination;
            }
        }
        if (database == null) throw new IllegalStateException("H2 backup contains no database file.");
        String databasePath = database.toString().substring(0, database.toString().length() - ".mv.db".length());
        String cloneUrl = "jdbc:h2:file:" + databasePath + ";IFEXISTS=TRUE;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE";
        var cipher = java.util.regex.Pattern.compile("(?i);CIPHER=(AES|XTEA|FOG)").matcher(sourceUrl);
        if (cipher.find()) cloneUrl += ";CIPHER=" + cipher.group(1);
        Path script = directory.resolve("h2-snapshot.sql");
        Map<String, Long> counts = new TreeMap<>();
        try (Connection snapshot = DriverManager.getConnection(cloneUrl, properties.determineUsername(), properties.determinePassword())) {
            try (var statement = snapshot.createStatement()) { statement.execute("SCRIPT NOSETTINGS TO " + literal(script) + " CHARSET 'UTF-8'"); }
            try (var tables = snapshot.getMetaData().getTables(null, "PUBLIC", "%", new String[]{"TABLE", "BASE TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    try (var statement = snapshot.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM \"PUBLIC\".\"" + name.replace("\"", "\"\"") + "\"")) {
                        rows.next(); counts.put(name.toLowerCase(java.util.Locale.ROOT), rows.getLong(1));
                    }
                }
            }
        }
        Path manifest = directory.resolve("manifest.json");
        json.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), Map.of(
                "format", "swarnabhoomi-h2-backup-v1", "createdAt", createdAt.toString(), "tables", counts,
                "nativeBackup", nativeBackup.getFileName().toString(), "logicalBackup", script.getFileName().toString()));
        Path archive = directory.resolve("render-h2-export.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Path file : new Path[]{nativeBackup, script, manifest}) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString())); Files.copy(file, zip); zip.closeEntry();
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(archive)) { byte[] buffer = new byte[65_536]; for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read); }
        cached = new Snapshot(archive, HexFormat.of().formatHex(digest.digest()), createdAt, counts);
        return cached;
    }

    private static String literal(Path path) { return "'" + path.toString().replace("\\", "/").replace("'", "''") + "'"; }
    public record Snapshot(Path archive, String sha256, Instant createdAt, Map<String, Long> counts) {}
}
