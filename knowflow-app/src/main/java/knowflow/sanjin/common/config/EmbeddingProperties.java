package knowflow.sanjin.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Embedding 系统级配置：单一云端 Embedding 提供者，不进入用户 ModelConfig 页面。 */
@ConfigurationProperties(prefix = "knowflow.embedding")
public class EmbeddingProperties {

  /** OpenAI 兼容 Base URL，例如 https://dashscope.aliyuncs.com/compatible-mode/v1。 */
  private String baseUrl = "";

  private String apiKey = "";

  private String model = "text-embedding-v4";

  private int dimensions = 1024;

  private int connectTimeoutMillis = 10_000;

  private int readTimeoutMillis = 60_000;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public int getDimensions() {
    return dimensions;
  }

  public void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  public int getConnectTimeoutMillis() {
    return connectTimeoutMillis;
  }

  public void setConnectTimeoutMillis(int connectTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
  }

  public int getReadTimeoutMillis() {
    return readTimeoutMillis;
  }

  public void setReadTimeoutMillis(int readTimeoutMillis) {
    this.readTimeoutMillis = readTimeoutMillis;
  }
}
