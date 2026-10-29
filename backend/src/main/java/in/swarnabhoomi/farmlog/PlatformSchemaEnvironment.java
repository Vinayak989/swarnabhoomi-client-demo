package in.swarnabhoomi.farmlog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Additive schema upgrade before Hibernate's production validate step. Safe to retry after a DDL
 * interruption.
 */
public class PlatformSchemaEnvironment implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
    String url = env.getProperty("spring.datasource.url", "");
    if (!url.startsWith("jdbc:mysql:")) return;
    try (Connection c =
        DriverManager.getConnection(
            url,
            env.getProperty("spring.datasource.username"),
            env.getProperty("spring.datasource.password"))) {
      boolean commerceComplete = applied(c, "commerce-v1");
      boolean consultantRemovalComplete = applied(c, "consultant-removal-v2");
      if (commerceComplete && consultantRemovalComplete) return;
      MySqlLogicalBackup.create(
          url,
          env.getProperty("spring.datasource.username"),
          env.getProperty("spring.datasource.password"),
          Path.of(env.getProperty("DB_BACKUP_DIRECTORY", "/var/data/backups"), "mysql"));
      try (var s = c.createStatement()) {
        if (!commerceComplete) {
          for (String column :
              new String[] {
                "offer_starts_at TIMESTAMP(6) NULL",
                "offer_ends_at TIMESTAMP(6) NULL",
                "harvest_date DATE NULL",
                "batch_number VARCHAR(80) NULL",
                "traceability_notes VARCHAR(1000) NULL",
                "traceability_public BOOLEAN NOT NULL DEFAULT FALSE",
                "traceability_token VARCHAR(36) NULL"
              }) {
            String name = column.substring(0, column.indexOf(' '));
            try (var cols =
                c.getMetaData().getColumns(c.getCatalog(), null, "farm_product_listing", name)) {
              if (!cols.next()) s.execute("ALTER TABLE farm_product_listing ADD COLUMN " + column);
            }
          }
          try (var input =
              PlatformSchemaEnvironment.class.getResourceAsStream("/db/platform-v1.sql")) {
            for (String sql : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";"))
              if (!sql.isBlank()) s.execute(sql);
          }
          PlatformTables.ensureIndexes(c);
          s.execute(
              "INSERT INTO platform_migration(version,applied_at)"
                  + " VALUES('commerce-v1',CURRENT_TIMESTAMP)");
          org.slf4j.LoggerFactory.getLogger(getClass())
              .info("Platform schema commerce-v1 applied after a native MySQL backup.");
        }
        if (!consultantRemovalComplete) {
          try (var columns =
              c.getMetaData()
                  .getColumns(c.getCatalog(), null, "organic_consultant", "deleted_at")) {
            if (!columns.next())
              s.execute("ALTER TABLE organic_consultant ADD COLUMN deleted_at TIMESTAMP(6) NULL");
          }
          s.execute(
              "INSERT INTO platform_migration(version,applied_at)"
                  + " VALUES('consultant-removal-v2',CURRENT_TIMESTAMP)");
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "Platform migration failed. Existing records are preserved; check database permissions"
              + " and native backup availability.",
          e);
    }
  }

  private static boolean applied(Connection connection, String version) throws SQLException {
    try (var tables =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), null, "platform_migration", null)) {
      if (!tables.next()) return false;
    }
    try (var query =
        connection.prepareStatement("SELECT version FROM platform_migration WHERE version=?")) {
      query.setString(1, version);
      try (var rows = query.executeQuery()) {
        return rows.next();
      }
    }
  }
}
