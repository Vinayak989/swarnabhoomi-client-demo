package in.swarnabhoomi.farmlog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class HealthController {
    private final DataSource database;
    private final Environment environment;

    public HealthController(DataSource database, Environment environment) { this.database = database; this.environment = environment; }

    @GetMapping("/api/health") public Map<String,String> health() {
        Map<String,String> result = new LinkedHashMap<>(); result.put("status", "ok");
        try (var connection = database.getConnection(); var query = connection.createStatement()) {
            query.setQueryTimeout(3);
            try (var row = query.executeQuery("SELECT 1")) { if (!row.next() || row.getInt(1) != 1) throw new IllegalStateException(); }
            result.put("database", connection.getMetaData().getDatabaseProductName());
        } catch (Exception exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database is not ready."); }
        for (var field : Map.of("mysqlTargetCheck", "app.database.mysql-target-check", "mysqlTargetError", "app.database.mysql-target-error",
                "migratedTables", "app.database.migrated-tables", "migratedRows", "app.database.migrated-rows").entrySet()) {
            String value = environment.getProperty(field.getValue()); if (value != null) result.put(field.getKey(), value);
        }
        return result;
    }
}
