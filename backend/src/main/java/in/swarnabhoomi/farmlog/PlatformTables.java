package in.swarnabhoomi.farmlog;

import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@DependsOn("entityManagerFactory")
public class PlatformTables implements InitializingBean {
  private final DataSource dataSource;

  public PlatformTables(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void afterPropertiesSet() {
    new ResourceDatabasePopulator(new ClassPathResource("db/platform-v1.sql")).execute(dataSource);
    new org.springframework.jdbc.core.JdbcTemplate(dataSource)
        .update(
            "INSERT INTO platform_setting(setting_key,setting_value) SELECT 'delivery-lock','1'"
                + " WHERE NOT EXISTS(SELECT 1 FROM platform_setting WHERE"
                + " setting_key='delivery-lock')");
    try (var connection = dataSource.getConnection()) {
      ensureIndexes(connection);
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("Platform indexes could not be verified", e);
    }
  }

  static void ensureIndexes(java.sql.Connection connection) throws java.sql.SQLException {
    String[][] indexes = {
      {"trade_subscription", "ix_subscription_due", "status,next_date,id"},
      {"trade_subscription", "ix_subscription_buyer", "buyer_id,id"},
      {"trade_bulk", "ix_bulk_open", "status,required_date,id"},
      {"trade_bulk", "ix_bulk_buyer", "buyer_id,id"},
      {"trade_quote", "ix_quote_prices", "bulk_id,unit_price,id"},
      {"trade_return", "ix_return_buyer", "buyer_id,id"},
      {"trade_return", "ix_return_seller", "seller_id,id"},
      {"farm_material", "ix_material_owner", "owner_id,name"},
      {"farm_material_movement", "ix_material_entry", "owner_id,entry_id,material_id"},
      {"farm_material_movement", "ix_material_history", "material_id,id"},
      {"farm_product_listing", "ix_listing_batch", "traceability_token"}
    };
    for (var index : indexes) {
      boolean exists = false;
      String table =
          connection.getMetaData().storesUpperCaseIdentifiers()
              ? index[0].toUpperCase(java.util.Locale.ROOT)
              : index[0];
      try (var rows =
          connection
              .getMetaData()
              .getIndexInfo(connection.getCatalog(), null, table, false, false)) {
        while (rows.next())
          if (index[1].equalsIgnoreCase(rows.getString("INDEX_NAME"))) {
            exists = true;
            break;
          }
      }
      if (!exists)
        try (var statement = connection.createStatement()) {
          statement.execute("CREATE INDEX " + index[1] + " ON " + index[0] + " (" + index[2] + ")");
        }
    }
  }
}
