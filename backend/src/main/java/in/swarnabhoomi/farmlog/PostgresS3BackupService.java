package in.swarnabhoomi.farmlog;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Component
@ConditionalOnProperty(name = "app.database.backup.enabled", havingValue = "true")
public class PostgresS3BackupService {
    private static final Logger log = LoggerFactory.getLogger(PostgresS3BackupService.class);

    private final S3Client s3Client;
    private final String bucket;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final Path backupDirectory;

    public PostgresS3BackupService(S3Client s3Client,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${app.database.backup.directory:/var/data/backups}") String backupDirectory) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.backupDirectory = Path.of(backupDirectory);
        if (bucket.isBlank()) {
            throw new IllegalStateException("S3_BUCKET is required when DATABASE_BACKUP_ENABLED is true.");
        }
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("S3 database backups require a PostgreSQL datasource.");
        }
    }

    @Scheduled(cron = "${app.database.backup.cron:0 0 2 * * *}", zone = "UTC")
    public void backupToS3() {
        Instant started = Instant.now();
        Path dumpFile = null;
        Path errorFile = null;
        Process process = null;
        try {
            Files.createDirectories(backupDirectory);
            dumpFile = Files.createTempFile(backupDirectory, "swarnabhoomi-", ".dump");
            errorFile = Files.createTempFile(backupDirectory, "swarnabhoomi-pg-dump-", ".log");
            URI database = URI.create(jdbcUrl.substring("jdbc:".length()));
            String databaseName = database.getPath().replaceFirst("^/", "");
            if (databaseName.isBlank() || username.isBlank()) {
                throw new IllegalStateException("PostgreSQL database name and username are required for backups.");
            }

            ProcessBuilder builder = new ProcessBuilder(
                    "pg_dump", "--format=custom", "--no-owner", "--no-acl",
                    "--host", database.getHost(),
                    "--port", Integer.toString(database.getPort() > 0 ? database.getPort() : 5432),
                    "--username", username,
                    "--dbname", databaseName,
                    "--file", dumpFile.toString());
            builder.environment().put("PGPASSWORD", password);
            builder.environment().put("PGSSLMODE", "require");
            builder.redirectError(errorFile.toFile());
            process = builder.start();
            if (!process.waitFor(Duration.ofMinutes(30).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("pg_dump exceeded the 30-minute backup timeout.");
            }
            if (process.exitValue() != 0) {
                String details = Files.readString(errorFile).trim();
                log.error("PostgreSQL S3 backup failed (exit {}). {}", process.exitValue(),
                        details.isBlank() ? "pg_dump returned no details." : details);
                throw new IllegalStateException("pg_dump failed with exit code " + process.exitValue() + ".");
            }
            if (Files.size(dumpFile) == 0) {
                throw new IllegalStateException("pg_dump produced an empty backup.");
            }

            Instant finished = Instant.now();
            String key = "backups/postgres/" + started.toString().replace(":", "-") + ".dump";
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/octet-stream")
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .metadata(java.util.Map.of("started-at", started.toString(),
                                    "finished-at", finished.toString()))
                            .build(),
                    RequestBody.fromFile(dumpFile));
            log.info("PostgreSQL backup uploaded to s3://{}/{} ({} bytes).", bucket, key, Files.size(dumpFile));
        } catch (InterruptedException exception) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            log.error("PostgreSQL S3 backup was interrupted.", exception);
            throw new IllegalStateException("PostgreSQL S3 backup was interrupted.", exception);
        } catch (IOException | RuntimeException exception) {
            log.error("PostgreSQL S3 backup failed.", exception);
            throw new IllegalStateException("PostgreSQL S3 backup failed.", exception);
        } finally {
            deleteTemporaryFile(dumpFile);
            deleteTemporaryFile(errorFile);
        }
    }

    private void deleteTemporaryFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Could not delete temporary database backup file {}.", path.getFileName(), exception);
        }
    }
}
