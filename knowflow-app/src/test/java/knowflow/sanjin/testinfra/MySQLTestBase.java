package knowflow.sanjin.testinfra;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL 集成测试基类：用 Testcontainers 起真实 MySQL 8.4，并把数据源指向它。
 *
 * <p>所有涉及真实数据库语义的 IT 均继承本类；不用 H2 模拟 MySQL。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MySQLTestBase {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("knowflow_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }
}
