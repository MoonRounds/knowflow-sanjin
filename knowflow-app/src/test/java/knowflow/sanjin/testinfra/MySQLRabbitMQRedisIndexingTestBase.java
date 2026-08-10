package knowflow.sanjin.testinfra;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL + RabbitMQ + Qdrant + Redis 全链路集成测试基类。
 *
 * <p>用于 RAG 纵切：知识索引（MySQL+RabbitMQ+Qdrant）与 Chat Memory（Redis）在同一测试中可用。
 */
@Testcontainers
public abstract class MySQLRabbitMQRedisIndexingTestBase extends MySQLRabbitMQIndexingTestBase {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void configureRedis(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }
}
