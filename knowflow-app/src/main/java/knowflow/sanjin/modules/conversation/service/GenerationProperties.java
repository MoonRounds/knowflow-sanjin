package knowflow.sanjin.modules.conversation.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 3 Generation 相关配置：上下文窗口、超时清理、执行线程池。 */
@ConfigurationProperties(prefix = "knowflow.generation")
public class GenerationProperties {

  /** 从 MySQL 构造上下文时使用的完整 active Turn 轮数。 */
  private int contextWindowTurns = 10;

  /** 每次生成的总体超时（挂起 Provider 流的总兜底）。 */
  private Duration totalTimeout = Duration.ofSeconds(90);

  /** 僵死 active generation 的清理阈值；超过视为失败并释放 slot。 */
  private Duration staleTimeout = Duration.ofMinutes(5);

  /** Generation 执行线程池大小。 */
  private int maxConcurrency = 8;

  public int getContextWindowTurns() {
    return contextWindowTurns;
  }

  public void setContextWindowTurns(int contextWindowTurns) {
    this.contextWindowTurns = contextWindowTurns;
  }

  public Duration getTotalTimeout() {
    return totalTimeout;
  }

  public void setTotalTimeout(Duration totalTimeout) {
    this.totalTimeout = totalTimeout;
  }

  public Duration getStaleTimeout() {
    return staleTimeout;
  }

  public void setStaleTimeout(Duration staleTimeout) {
    this.staleTimeout = staleTimeout;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }
}
