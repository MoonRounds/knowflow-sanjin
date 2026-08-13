package knowflow.sanjin.modules.embeddingconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 系统级向量模型配置响应：API Key 只回显掩码；configured=false 表示尚未在系统设置中保存过配置。 */
public class EmbeddingConfigResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean configured;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String baseUrl;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String modelName;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String apiKeyMasked;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer dimension;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant updatedAt;

  public boolean isConfigured() {
    return configured;
  }

  public void setConfigured(boolean configured) {
    this.configured = configured;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getApiKeyMasked() {
    return apiKeyMasked;
  }

  public void setApiKeyMasked(String apiKeyMasked) {
    this.apiKeyMasked = apiKeyMasked;
  }

  public Integer getDimension() {
    return dimension;
  }

  public void setDimension(Integer dimension) {
    this.dimension = dimension;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
