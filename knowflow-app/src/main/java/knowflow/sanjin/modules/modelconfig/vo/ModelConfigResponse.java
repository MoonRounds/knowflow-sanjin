package knowflow.sanjin.modules.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** ModelConfig API 响应：id 为字符串；currentRevision 是当前生效的 Revision 摘要（含掩码 Key）。 */
public class ModelConfigResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String displayName;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String providerName;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean enabled;

  private String currentRevisionId;
  private RevisionSummary currentRevision;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant createdAt;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCurrentRevisionId() {
    return currentRevisionId;
  }

  public void setCurrentRevisionId(String currentRevisionId) {
    this.currentRevisionId = currentRevisionId;
  }

  public RevisionSummary getCurrentRevision() {
    return currentRevision;
  }

  public void setCurrentRevision(RevisionSummary currentRevision) {
    this.currentRevision = currentRevision;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public static class RevisionSummary {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private int revisionNo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String providerName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String baseUrl;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String modelName;

    private Double temperature;
    private Integer maxOutputTokens;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String apiKeyMasked;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public int getRevisionNo() {
      return revisionNo;
    }

    public void setRevisionNo(int revisionNo) {
      this.revisionNo = revisionNo;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public String getProviderName() {
      return providerName;
    }

    public void setProviderName(String providerName) {
      this.providerName = providerName;
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

    public Double getTemperature() {
      return temperature;
    }

    public void setTemperature(Double temperature) {
      this.temperature = temperature;
    }

    public Integer getMaxOutputTokens() {
      return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
      this.maxOutputTokens = maxOutputTokens;
    }

    public String getApiKeyMasked() {
      return apiKeyMasked;
    }

    public void setApiKeyMasked(String apiKeyMasked) {
      this.apiKeyMasked = apiKeyMasked;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }
  }
}
