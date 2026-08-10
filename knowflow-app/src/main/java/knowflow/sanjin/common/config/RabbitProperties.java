package knowflow.sanjin.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** RabbitMQ 拓扑与恢复策略配置：工作队列、TTL 重试档位、超时与扫描间隔。 */
@ConfigurationProperties(prefix = "knowflow.rabbit")
public class RabbitProperties {

  private String prefix = "knowflow";

  private String workQueue = "index.work";

  private Duration[] retryDelays = {
    Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5)
  };

  private Duration processingLeaseTimeout = Duration.ofMinutes(10);

  private Duration recoveryScanInterval = Duration.ofMinutes(1);

  /** 工作交换机名。 */
  public String workExchange() {
    return prefix + ".work.exchange";
  }

  /** 重试交换机名。 */
  public String retryExchange() {
    return prefix + ".retry.exchange";
  }

  /** DLX 交换机名。 */
  public String dlxExchange() {
    return prefix + ".dlx.exchange";
  }

  /** 工作队列名。 */
  public String workQueueName() {
    return prefix + "." + workQueue;
  }

  /** 第 level 档重试队列名（level 从 0 开始）。 */
  public String retryQueueName(int level) {
    return prefix + "." + workQueue + ".retry." + level;
  }

  /** 最终 DLQ 名。 */
  public String dlqName() {
    return prefix + "." + workQueue + ".dlq";
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getWorkQueue() {
    return workQueue;
  }

  public void setWorkQueue(String workQueue) {
    this.workQueue = workQueue;
  }

  public Duration[] getRetryDelays() {
    return retryDelays;
  }

  public void setRetryDelays(Duration[] retryDelays) {
    this.retryDelays = retryDelays;
  }

  public Duration getProcessingLeaseTimeout() {
    return processingLeaseTimeout;
  }

  public void setProcessingLeaseTimeout(Duration processingLeaseTimeout) {
    this.processingLeaseTimeout = processingLeaseTimeout;
  }

  public Duration getRecoveryScanInterval() {
    return recoveryScanInterval;
  }

  public void setRecoveryScanInterval(Duration recoveryScanInterval) {
    this.recoveryScanInterval = recoveryScanInterval;
  }
}
