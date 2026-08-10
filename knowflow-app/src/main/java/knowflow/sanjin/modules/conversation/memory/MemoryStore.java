package knowflow.sanjin.modules.conversation.memory;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Chat Memory 投影的存储抽象。Redis 实现用于生产，内存实现用于单元测试。
 *
 * <p>存储层不吞 Redis 异常：连接失败等异常向上抛给 {@link MemoryService}，由其在 MySQL 降级路径处理。
 */
public interface MemoryStore {

  Optional<MemorySnapshot> get(String key);

  void save(String key, MemorySnapshot snapshot, Duration ttl);

  void delete(String key);

  /** 按 key 前缀列出匹配的键（供 findConversationIds 使用）。 */
  Set<String> keys(String prefix);
}
