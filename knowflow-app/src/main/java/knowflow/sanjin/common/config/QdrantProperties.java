package knowflow.sanjin.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Qdrant 配置：内部基础设施，默认 localhost，不进入公网 SSRF 校验路径。 */
@ConfigurationProperties(prefix = "knowflow.qdrant")
public class QdrantProperties {

  private String baseUrl = "http://127.0.0.1:6333";

  private String collectionName = "knowflow_dense_v1";

  private int connectTimeoutMillis = 5_000;

  private int readTimeoutMillis = 30_000;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getCollectionName() {
    return collectionName;
  }

  public void setCollectionName(String collectionName) {
    this.collectionName = collectionName;
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
