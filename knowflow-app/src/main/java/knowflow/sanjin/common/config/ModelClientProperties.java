package knowflow.sanjin.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 云端模型客户端的基础超时与并发限制。 */
@ConfigurationProperties(prefix = "knowflow.model-client")
public class ModelClientProperties {

  /** 连接超时。 */
  private Duration connectTimeout = Duration.ofSeconds(10);

  /** 读取超时。 */
  private Duration readTimeout = Duration.ofSeconds(60);

  /** 总体调用超时。 */
  private Duration totalTimeout = Duration.ofSeconds(90);

  /** 同步 ChatModel 并发上限。 */
  private int maxConcurrency = 8;

  /** 本地开发是否允许 http://localhost 等本地 Stub 地址（默认 false，测试环境开启）。 */
  private boolean allowLocalBaseUrl = false;

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public Duration getTotalTimeout() {
    return totalTimeout;
  }

  public void setTotalTimeout(Duration totalTimeout) {
    this.totalTimeout = totalTimeout;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }

  public boolean isAllowLocalBaseUrl() {
    return allowLocalBaseUrl;
  }

  public void setAllowLocalBaseUrl(boolean allowLocalBaseUrl) {
    this.allowLocalBaseUrl = allowLocalBaseUrl;
  }
}
