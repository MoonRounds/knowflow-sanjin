package knowflow.sanjin.testinfra;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

/**
 * MySQL + RabbitMQ + Qdrant 集成测试基类：Testcontainers 起真实 MySQL、RabbitMQ 与 Qdrant。
 *
 * <p>Qdrant 暴露 REST 端口（6333），供薄 HTTP 客户端访问。本基类用于知识索引纵切集成测试。
 */
public abstract class MySQLRabbitMQIndexingTestBase extends MySQLRabbitMQTestBase {

  @Container
  static final GenericContainer<?> QDRANT =
      new GenericContainer<>("qdrant/qdrant:v1.12.4")
          .withExposedPorts(6333, 6334)
          .waitingFor(Wait.forHttp("/").forPort(6333).forStatusCode(200));

  @DynamicPropertySource
  static void configureQdrant(DynamicPropertyRegistry registry) {
    registry.add(
        "knowflow.qdrant.base-url",
        () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
    registry.add("knowflow.qdrant.collection-name", () -> "knowflow_it_dense");
  }
}
