package in.swarnabhoomi.farmlog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PhotoSchemaMigrationTest {
    @Test
    void widensExistingH2PhotoColumnWithoutLosingPhotos() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:photo_column_upgrade;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE farm_photo (id VARCHAR(36) PRIMARY KEY, photo_data VARBINARY(255))");
        byte[] original = {1, 2, 3};
        jdbc.update("INSERT INTO farm_photo (id, photo_data) VALUES (?, ?)", "original", original);

        new PhotoSchemaMigration(jdbc).run(null);

        byte[] largePhoto = new byte[2_000_000];
        Arrays.fill(largePhoto, (byte) 42);
        jdbc.update("INSERT INTO farm_photo (id, photo_data) VALUES (?, ?)", "large", largePhoto);
        assertArrayEquals(original, jdbc.queryForObject(
                "SELECT photo_data FROM farm_photo WHERE id = ?", byte[].class, "original"));
        assertArrayEquals(largePhoto, jdbc.queryForObject(
                "SELECT photo_data FROM farm_photo WHERE id = ?", byte[].class, "large"));
    }
}
