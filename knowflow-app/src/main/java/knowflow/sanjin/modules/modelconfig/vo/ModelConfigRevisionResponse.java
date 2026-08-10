package knowflow.sanjin.modules.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Revision 响应：只返回掩码，不返回明文或密文。 */
public class ModelConfigRevisionResponse {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String modelConfigId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int revisionNo;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String providerType;

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
  private int apiKeyEncryptionVersion;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(String modelConfigId) {
    this.modelConfigId = modelConfigId;
  }

  public int getRevisionNo() {
    return revisionNo;
  }

  public void setRevisionNo(int revisionNo) {
    this.revisionNo = revisionNo;
  }

  public String getProviderType() {
    return providerType;
  }

  public void setProviderType(String providerType) {
    this.providerType = providerType;
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

  public int getApiKeyEncryptionVersion() {
    return apiKeyEncryptionVersion;
  }

  public void setApiKeyEncryptionVersion(int apiKeyEncryptionVersion) {
    this.apiKeyEncryptionVersion = apiKeyEncryptionVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
