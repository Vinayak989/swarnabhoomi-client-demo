package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class MySqlCutoverEnvironmentTest {
    @Test void normalStartupDoesNotReadOrChangeDatabaseSettings() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:h2:mem:normal");
        new MySqlCutoverEnvironment().postProcessEnvironment(environment, new SpringApplication(FarmLogApplication.class));
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:normal");
        assertThat(environment.getProperty("app.database.mysql-target-check")).isNull();
    }

    @Test void checkWithMissingCredentialsLeavesH2SelectedAndReportsFailure() {
        var environment = new MockEnvironment().withProperty("APP_MYSQL_CUTOVER_MODE", "check")
                .withProperty("spring.datasource.url", "jdbc:h2:mem:normal");
        new MySqlCutoverEnvironment().postProcessEnvironment(environment, new SpringApplication(FarmLogApplication.class));
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:normal");
        assertThat(environment.getProperty("app.database.mysql-target-check")).isEqualTo("failed");
    }

    @Test void applyWithMissingCredentialsStopsBeforeTheAppCanAcceptWrites() {
        var environment = new MockEnvironment().withProperty("APP_MYSQL_CUTOVER_MODE", "apply")
                .withProperty("spring.datasource.url", "jdbc:h2:mem:normal");
        assertThatThrownBy(() -> new MySqlCutoverEnvironment().postProcessEnvironment(environment, new SpringApplication(FarmLogApplication.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Original H2 retained");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:normal");
    }

    @Test void credentialsCannotBeEmbeddedInUrlOrDiagnosticString() {
        var environment = new MockEnvironment().withProperty("APP_MYSQL_CUTOVER_MODE", "check")
                .withProperty("MYSQL_JDBC_URL", "jdbc:mysql://example/db?password=private-test-value")
                .withProperty("MYSQL_USER", "test").withProperty("MYSQL_PASSWORD", "private-test-value");
        new MySqlCutoverEnvironment().postProcessEnvironment(environment, new SpringApplication(FarmLogApplication.class));
        assertThat(environment.getProperty("app.database.mysql-target-check")).isEqualTo("failed");
        assertThat(new H2ToMySqlMigration.Target("jdbc:mysql://example/db", "test", "private-test-value", "db").toString())
                .doesNotContain("private-test-value");
    }
}
