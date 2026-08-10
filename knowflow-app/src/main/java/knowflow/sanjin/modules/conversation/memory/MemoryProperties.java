package knowflow.sanjin.modules.conversation.memory;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 4 Chat Memory 投影配置：窗口轮数、TTL、Redis key 前缀与序列化 schema 版本。 */
@ConfigurationProperties(prefix = "knowflow.chat-memory")
public class MemoryProperties {

  /** 投影保存的最近完整 active Turn 轮数。 */
  private int turns = 10;

  /** 按不活跃时间刷新的 TTL；过期或清空后从 MySQL 重建。 */
  private Duration ttl = Duration.ofDays(7);

  /** Redis key 前缀，含 schema 命名空间与版本。 */
  private String keyPrefix = "knowflow:chat-memory:v1";

  /** 序列化 schema 版本；未来格式变更时用于迁移或兼容读取。 */
  private int schemaVersion = 1;

  public int getTurns() {
    return turns;
  }

  public void setTurns(int turns) {
    this.turns = turns;
  }

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }
}
