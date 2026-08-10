package knowflow.sanjin.modules.conversation.memory;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Redis 实现的 {@link MemoryStore}：JSON 序列化，TTL 在写入时刷新。 */
public class RedisMemoryStore implements MemoryStore {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public RedisMemoryStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<MemorySnapshot> get(String key) {
    String raw = redisTemplate.opsForValue().get(key);
    if (raw == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(raw, MemorySnapshot.class));
    } catch (Exception e) {
      // 反序列化失败视为缓存失效，触发 MySQL 重建
      return Optional.empty();
    }
  }

  @Override
  public void save(String key, MemorySnapshot snapshot, Duration ttl) {
    try {
      String raw = objectMapper.writeValueAsString(snapshot);
      redisTemplate.opsForValue().set(key, raw, ttl);
    } catch (Exception e) {
      throw new IllegalStateException("failed to serialize chat memory snapshot", e);
    }
  }

  @Override
  public void delete(String key) {
    redisTemplate.delete(key);
  }

  @Override
  public Set<String> keys(String prefix) {
    return redisTemplate.keys(prefix + "*");
  }
}
