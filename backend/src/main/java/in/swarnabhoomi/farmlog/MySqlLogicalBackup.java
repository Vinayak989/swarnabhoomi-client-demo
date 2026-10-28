package in.swarnabhoomi.farmlog;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/** Streaming, transactionally consistent native MySQL dump. No passwords in arguments or logs. */
final class MySqlLogicalBackup {
  static Path create(String jdbcUrl, String user, String password, Path directory)
      throws Exception {
    URI uri = URI.create(jdbcUrl.substring(5));
    String schema = uri.getPath().substring(1);
    if (!schema.matches("[A-Za-z0-9_]+") || uri.getHost() == null)
      throw new IllegalArgumentException("Invalid MySQL target");
    Files.createDirectories(directory);
    if (Files.getFileStore(directory).getUsableSpace() < 256L * 1024 * 1024)
      throw new IOException("Insufficient backup space");
    String id =
        "mysql-"
            + Instant.now().toString().replace(':', '-')
            + "-"
            + UUID.randomUUID().toString().substring(0, 8);
    Path pending = directory.resolve(id + ".pending"),
        destination = directory.resolve(id + ".sql.gz");
    String configured = System.getenv("MYSQLDUMP_PATH");
    List<String> args = new ArrayList<>();
    if (configured != null && !configured.isBlank()) args.add(configured);
    else if (Files.isRegularFile(Path.of("/opt/mysql/lib/ld-linux-x86-64.so.2")))
      args.addAll(
          List.of(
              "/opt/mysql/lib/ld-linux-x86-64.so.2",
              "--library-path",
              "/opt/mysql/lib",
              "/opt/mysql/bin/mysqldump"));
    else args.add("mysqldump");
    args.addAll(
        List.of(
            "--host=" + uri.getHost(),
            "--port=" + (uri.getPort() < 0 ? 3306 : uri.getPort()),
            "--user=" + user,
            "--single-transaction",
            "--quick",
            "--hex-blob",
            "--no-tablespaces",
            "--skip-lock-tables",
            "--set-gtid-purged=OFF",
            "--column-statistics=0",
            "--ssl-mode=REQUIRED",
            schema));
    ProcessBuilder builder = new ProcessBuilder(args);
    builder.environment().put("MYSQL_PWD", password);
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
    Process process = builder.start();
    Thread timeout =
        new Thread(
            () -> {
              try {
                if (!process.waitFor(10, TimeUnit.MINUTES)) process.destroyForcibly();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            "mysql-backup-timeout");
    timeout.setDaemon(true);
    timeout.start();
    try {
      try (var input = process.getInputStream();
          var output =
              new GZIPOutputStream(Files.newOutputStream(pending, StandardOpenOption.CREATE_NEW))) {
        input.transferTo(output);
      }
      if (!process.waitFor(10, TimeUnit.MINUTES)
          || process.exitValue() != 0
          || Files.size(pending) < 50) throw new IOException("MySQL backup failed");
      Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE);
      try {
        Files.setPosixFilePermissions(
            destination, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
      }
      return destination;
    } finally {
      process.destroyForcibly();
      Files.deleteIfExists(pending);
    }
  }
}
