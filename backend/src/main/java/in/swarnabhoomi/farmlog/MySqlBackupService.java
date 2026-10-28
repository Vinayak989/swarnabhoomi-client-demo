package in.swarnabhoomi.farmlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class MySqlBackupService implements AutoCloseable {
  private final Environment env;
  private final ObjectProvider<S3Client> s3;
  private final ObjectMapper json;
  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "mysql-backup");
            t.setDaemon(true);
            return t;
          });
  private volatile String lastError;
  private volatile Instant lastSuccess;

  @Value("${DB_BACKUP_DIRECTORY:/var/data/backups}")
  private String root;

  @Value("${app.storage.s3.bucket:}")
  private String bucket;

  public MySqlBackupService(Environment env, ObjectProvider<S3Client> s3, ObjectMapper json) {
    this.env = env;
    this.s3 = s3;
    this.json = json;
  }

  boolean enabled() {
    return env.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql:")
        && env.getProperty("app.database.mysql-backup.enabled", Boolean.class, true);
  }

  Path directory() {
    return Path.of(root, "mysql").toAbsolutePath().normalize();
  }

  @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Kolkata")
  public void nightly() {
    if (enabled()) start();
  }

  @org.springframework.context.event.EventListener(
      org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void catchUp() {
    if (!enabled()) return;
    try {
      var saved = files();
      if (saved.isEmpty()
          || Files.getLastModifiedTime(saved.get(0))
              .toInstant()
              .isBefore(Instant.now().minus(java.time.Duration.ofHours(24)))) start();
    } catch (Exception e) {
      lastError = "Backup readiness check failed.";
    }
  }

  public void start() {
    if (!enabled()) throw TradeService.conflict("MySQL स्वयंचलित बॅकअप या डेटाबेससाठी सुरू नाही.");
    if (!running.compareAndSet(false, true)) throw TradeService.conflict("बॅकअप आधीच सुरू आहे.");
    worker.submit(
        () -> {
          try {
            Path file =
                MySqlLogicalBackup.create(
                    env.getProperty("spring.datasource.url"),
                    env.getProperty("spring.datasource.username"),
                    env.getProperty("spring.datasource.password"),
                    directory());
            String hash;
            try (var input = Files.newInputStream(file)) {
              var digest = MessageDigest.getInstance("SHA-256");
              byte[] buffer = new byte[65536];
              int n;
              while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
              hash = HexFormat.of().formatHex(digest.digest());
            }
            boolean remote = false;
            if (!bucket.isBlank() && s3.getIfAvailable() != null) {
              s3.getObject()
                  .putObject(
                      PutObjectRequest.builder()
                          .bucket(bucket)
                          .key("database-backups/" + file.getFileName())
                          .contentType("application/gzip")
                          .serverSideEncryption(ServerSideEncryption.AES256)
                          .build(),
                      RequestBody.fromFile(file));
              remote = true;
            }
            lastSuccess = Instant.now();
            json.writerWithDefaultPrettyPrinter()
                .writeValue(
                    file.resolveSibling(file.getFileName() + ".json").toFile(),
                    Map.of(
                        "createdAt",
                        lastSuccess.toString(),
                        "sha256",
                        hash,
                        "bytes",
                        Files.size(file),
                        "offsite",
                        remote,
                        "format",
                        "mysqldump-gzip"));
            lastError = null;
            prune();
          } catch (Exception e) {
            lastError =
                "Backup failed. Check database access, free disk space and native MySQL client.";
            org.slf4j.LoggerFactory.getLogger(getClass())
                .error("Automatic MySQL backup failed ({}).", e.getClass().getSimpleName());
          } finally {
            running.set(false);
          }
        });
  }

  List<Path> files() throws Exception {
    if (!Files.isDirectory(directory())) return List.of();
    try (var paths = Files.list(directory())) {
      return paths
          .filter(p -> p.getFileName().toString().matches("mysql-[A-Za-z0-9.:-]+\\.sql\\.gz"))
          .sorted(Comparator.reverseOrder())
          .toList();
    }
  }

  void prune() throws Exception {
    var files = files();
    long total = 0;
    for (int i = 0; i < files.size(); i++) {
      Path p = files.get(i);
      total += Files.size(p);
      if (i >= 7 || (i > 0 && total > 1024L * 1024 * 1024)) {
        Files.deleteIfExists(p);
        Files.deleteIfExists(p.resolveSibling(p.getFileName() + ".json"));
      }
    }
  }

  public Map<String, Object> status() {
    var m = new LinkedHashMap<String, Object>();
    m.put("enabled", enabled());
    m.put("running", running.get());
    m.put("schedule", "दररोज पहाटे २:१५ (भारत)");
    m.put("retention", 7);
    m.put("lastSuccess", lastSuccess);
    m.put("error", lastError);
    m.put("offsiteConfigured", !bucket.isBlank() && s3.getIfAvailable() != null);
    try {
      var list = new ArrayList<Map<String, Object>>();
      for (Path p : files()) {
        var row = new LinkedHashMap<String, Object>();
        row.put("name", p.getFileName().toString());
        row.put("bytes", Files.size(p));
        row.put("createdAt", Files.getLastModifiedTime(p).toInstant().toString());
        Path manifest = p.resolveSibling(p.getFileName() + ".json");
        if (Files.isRegularFile(manifest)) {
          var info = json.readTree(manifest.toFile());
          row.put("sha256", info.path("sha256").asText());
          row.put("offsite", info.path("offsite").asBoolean());
        }
        list.add(row);
      }
      m.put("backups", list);
      m.put(
          "diskFreeBytes",
          Files.exists(directory()) ? Files.getFileStore(directory()).getUsableSpace() : null);
    } catch (Exception e) {
      m.put("error", "Backup list could not be read.");
    }
    return m;
  }

  public Path download(String name) {
    if (name == null || !name.matches("mysql-[A-Za-z0-9.:-]+\\.sql\\.gz"))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND);
    Path file = directory().resolve(name).normalize();
    if (!file.startsWith(directory()) || !Files.isRegularFile(file))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND);
    return file;
  }

  @Override
  public void close() {
    worker.shutdownNow();
  }
}
