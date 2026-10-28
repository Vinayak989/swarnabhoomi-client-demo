package in.swarnabhoomi.farmlog;

import java.sql.DriverManager;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Opt-in, fixed SQL maintenance. Credentials never enter the application data source or logs. */
public class MySqlMemoryMaintenance implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String admin = env.getProperty("MYSQL_ADMIN_USER", "");
        String password = env.getProperty("MYSQL_ADMIN_PASSWORD", "");
        String url = env.getProperty("MYSQL_JDBC_URL", "");
        if (admin.isBlank() || password.isBlank() || !url.startsWith("jdbc:mysql:")) return;
        String status = "failed";
        try (var connection = DriverManager.getConnection(url, admin, password);
                var query = connection.createStatement()) {
            query.setQueryTimeout(15);
            query.execute("SET PERSIST_ONLY performance_schema=OFF");
            for (String setting : new String[] {"innodb_buffer_pool_size=67108864", "innodb_log_buffer_size=8388608",
                    "max_connections=30", "table_open_cache=200", "thread_cache_size=4", "temptable_max_ram=16777216",
                    "tmp_table_size=16777216", "max_heap_table_size=16777216"}) query.execute("SET PERSIST " + setting);
            try (var rows = query.executeQuery("SELECT COUNT(*) FROM performance_schema.persisted_variables WHERE "
                    + "(VARIABLE_NAME='performance_schema' AND VARIABLE_VALUE IN ('OFF','0')) OR "
                    + "(VARIABLE_NAME='innodb_buffer_pool_size' AND VARIABLE_VALUE='67108864') OR "
                    + "(VARIABLE_NAME='max_connections' AND VARIABLE_VALUE='30')")) {
                status = rows.next() && rows.getInt(1) == 3 ? "persisted-restart-required" : "verification-failed";
            }
        } catch (Exception exception) {
            // JDBC exception messages may contain credentials or connection details. Expose only a fixed status.
        }
        env.getPropertySources().addFirst(new MapPropertySource("mysql-memory-maintenance",
                Map.of("app.database.mysql-memory-maintenance", status)));
    }
}
