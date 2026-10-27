package in.swarnabhoomi.farmlog;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PhotoSchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PhotoSchemaMigration.class);
    private static final int MAX_PHOTO_BYTES = 10_000_000;
    private final JdbcTemplate jdbc;

    public PhotoSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DataSource source = jdbc.getDataSource();
        if (source == null) return;

        try (Connection connection = source.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!"H2".equalsIgnoreCase(metadata.getDatabaseProductName())) return;
            try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "FARM_PHOTO", "PHOTO_DATA")) {
                if (!columns.next() || columns.getInt("COLUMN_SIZE") >= MAX_PHOTO_BYTES) return;
            }
        }

        jdbc.execute("ALTER TABLE farm_photo ALTER COLUMN photo_data BLOB");
        log.info("Upgraded farm_photo.photo_data to BLOB for image attachments.");
    }
}
