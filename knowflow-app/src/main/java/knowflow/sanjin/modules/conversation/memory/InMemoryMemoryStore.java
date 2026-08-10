package knowflow.sanjin.modules.conversation.memory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 测试用内存 {@link MemoryStore}：模拟 Redis 的键值语义，不处理 TTL 过期。 */
public class InMemoryMemoryStore implements MemoryStore {

  private final Map<String, MemorySnapshot> store = new ConcurrentHashMap<>();

  @Override
  public Optional<MemorySnapshot> get(String key) {
    return Optional.ofNullable(store.get(key));
  }

  @Override
  public void save(String key, MemorySnapshot snapshot, Duration ttl) {
    store.put(key, snapshot);
  }

  @Override
  public void delete(String key) {
    store.remove(key);
  }

  @Override
  public Set<String> keys(String prefix) {
    Set<String> result = new HashSet<>();
    for (String key : store.keySet()) {
      if (key.startsWith(prefix)) {
        result.add(key);
      }
    }
    return result;
  }
}
