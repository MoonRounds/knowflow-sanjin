package knowflow.sanjin.modules.conversation.memory;

import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Chat Memory 投影的生产 Bean：Redis 存储 + Spring AI 适配器。 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {

  @Bean
  public MemoryStore memoryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    return new RedisMemoryStore(redisTemplate, objectMapper);
  }

  @Bean
  public ChatMemoryRepositoryImpl chatMemoryRepository(
      MemoryStore memoryStore, MemoryProperties properties, CurrentOwnerProvider ownerProvider) {
    return new ChatMemoryRepositoryImpl(memoryStore, properties, ownerProvider);
  }
}
