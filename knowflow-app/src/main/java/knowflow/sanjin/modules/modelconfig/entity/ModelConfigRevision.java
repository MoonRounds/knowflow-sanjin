package knowflow.sanjin.modules.modelconfig.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 不可变 Revision：具体参数与加密 API Key 快照。创建后不更新、不删除。 */
@TableName("model_config_revision")
public class ModelConfigRevision {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long modelConfigId;

  private Long ownerId;

  private Integer revisionNo;

  private String providerType;

  private String displayName;

  private String providerName;

  private String baseUrl;

  private String modelName;

  private Double temperature;

  private Integer maxOutputTokens;

  private String encryptedApiKey;

  private Integer apiKeyEncryptionVersion;

  private String apiKeyMasked;

  @TableField(fill = FieldFill.INSERT)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(Long modelConfigId) {
    this.modelConfigId = modelConfigId;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public Integer getRevisionNo() {
    return revisionNo;
  }

  public void setRevisionNo(Integer revisionNo) {
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

  public String getEncryptedApiKey() {
    return encryptedApiKey;
  }

  public void setEncryptedApiKey(String encryptedApiKey) {
    this.encryptedApiKey = encryptedApiKey;
  }

  public Integer getApiKeyEncryptionVersion() {
    return apiKeyEncryptionVersion;
  }

  public void setApiKeyEncryptionVersion(Integer apiKeyEncryptionVersion) {
    this.apiKeyEncryptionVersion = apiKeyEncryptionVersion;
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
