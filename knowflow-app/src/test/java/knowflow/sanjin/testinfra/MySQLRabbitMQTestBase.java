package knowflow.sanjin.testinfra;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL + RabbitMQ 集成测试基类：Testcontainers 起真实 MySQL 8.4 与 RabbitMQ。
 *
 * <p>RabbitMQ 拓扑（work/retry/DLQ）由应用配置自动声明。本基类供异步索引消费链路集成测试使用。
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MySQLRabbitMQTestBase extends MySQLTestBase {

  @Container
  static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

  @DynamicPropertySource
  static void configureRabbit(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
    registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
  }
}
