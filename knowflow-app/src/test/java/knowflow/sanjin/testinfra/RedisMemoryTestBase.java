package knowflow.sanjin.testinfra;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 同时启动 MySQL 与普通 Redis 的集成测试基类。
 *
 * <p>Redis 仅作为 Chat Memory 投影的测试目标，每个测试前 {@link #clearRedis()} 清空，保证测试隔离。 MySQL 容器复用 {@link
 * MySQLTestBase}。
 */
@Testcontainers
public abstract class RedisMemoryTestBase extends MySQLTestBase {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @Autowired private RedisConnectionFactory redisConnectionFactory;

  @DynamicPropertySource
  static void configureRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @BeforeEach
  void resetRedis() {
    clearRedis();
  }

  /** 清空 Chat Memory 投影（测试隔离用）。 */
  protected void clearRedis() {
    var conn = redisConnectionFactory.getConnection();
    try {
      conn.serverCommands().flushDb();
    } finally {
      conn.close();
    }
  }

  /** 模拟 Redis 停机：CLIENT PAUSE 暂停响应 {@code millis} 毫秒，连接保留、读写超时，随后自动恢复。 */
  protected void pauseRedis(long millis) {
    StringRedisTemplate rt = new StringRedisTemplate(redisConnectionFactory);
    rt.execute(
        (org.springframework.data.redis.core.RedisCallback<Object>)
            connection ->
                connection.execute(
                    "CLIENT", "PAUSE".getBytes(), String.valueOf(millis).getBytes()));
  }
}
