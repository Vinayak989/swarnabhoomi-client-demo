package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class HealthControllerTest {
    @Test void h2HealthDoesNotUseMysqlSpecificQueries() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:health_controller_test", "sa", "");
        assertThat(new HealthController(source, new MockEnvironment()).health())
                .containsEntry("status", "ok").containsEntry("database", "H2")
                .doesNotContainKey("mysqlPerformanceSchema");
    }

    @Test void mysqlHealthReportsActiveSettingsWithoutConnectionCredentials() throws Exception {
        DataSource source = mock(DataSource.class); Connection connection = mock(Connection.class);
        Statement query = mock(Statement.class); DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet ping = mock(ResultSet.class), settings = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(query);
        when(connection.getMetaData()).thenReturn(metadata); when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(query.executeQuery("SELECT 1")).thenReturn(ping); when(ping.next()).thenReturn(true); when(ping.getInt(1)).thenReturn(1);
        when(query.executeQuery("SELECT @@GLOBAL.performance_schema, @@GLOBAL.innodb_buffer_pool_size, @@GLOBAL.max_connections")).thenReturn(settings);
        when(settings.next()).thenReturn(true); when(settings.getBoolean(1)).thenReturn(false);
        when(settings.getLong(2)).thenReturn(64L * 1024 * 1024); when(settings.getInt(3)).thenReturn(30);
        assertThat(new HealthController(source, new MockEnvironment()).health())
                .containsEntry("mysqlPerformanceSchema", "OFF").containsEntry("mysqlBufferPoolMiB", "64")
                .containsEntry("mysqlMaxConnections", "30").doesNotContainKey("password").doesNotContainKey("url");
        verify(query).setQueryTimeout(3);
    }

    @Test void unavailableDatabaseDoesNotReportHealthyOrExposeSqlErrors() throws Exception {
        DataSource source = mock(DataSource.class); when(source.getConnection()).thenThrow(new SQLException("private connection diagnostic"));
        assertThatThrownBy(() -> new HealthController(source, new MockEnvironment()).health())
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503").hasMessageNotContaining("private connection diagnostic");
    }
}
