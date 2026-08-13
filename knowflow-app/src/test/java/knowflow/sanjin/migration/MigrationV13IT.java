package knowflow.sanjin.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** V13 Conversation 手动知识库范围迁移：干净库和 V12 历史会话兼容。 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V13 conversation knowledge base migration")
class MigrationV13IT {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("knowflow_test")
          .withUsername("test")
          .withPassword("test");

  @BeforeEach
  void resetSchema() throws SQLException {
    try (Connection c = connection();
        Statement s = c.createStatement()) {
      s.execute("DROP DATABASE IF EXISTS knowflow_test");
      s.execute("CREATE DATABASE knowflow_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
  }

  @Test
  void cleanDatabaseHasNullableJsonColumn() throws Exception {
    migrate(null);
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA='knowflow_test' AND TABLE_NAME='conversation' "
                    + "AND COLUMN_NAME='knowledge_base_ids'")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("json");
      assertThat(rs.getString(2)).isEqualTo("YES");
    }
  }

  @Test
  void historicalConversationRemainsAutoAfterV13() throws Exception {
    migrate("12");
    try (Connection c = connection();
        Statement s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO conversation (id, owner_id, title, deleted, row_version) "
              + "VALUES (100, 1, 'legacy', 0, 0)");
    }
    migrate(null);
    try (Connection c = connection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT knowledge_base_ids FROM conversation WHERE id=100")) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isNull();
    }
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
  }

  private void migrate(String target) {
    var config =
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    if (target != null) {
      config.target(MigrationVersion.fromVersion(target));
    }
    config.load().migrate();
  }
}
