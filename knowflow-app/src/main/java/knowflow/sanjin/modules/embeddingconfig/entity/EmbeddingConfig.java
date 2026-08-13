package knowflow.sanjin.modules.embeddingconfig.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 系统级单一当前向量模型配置（id=1 单例）。API Key 加密存储，仅回显掩码。 */
@TableName("embedding_config")
public class EmbeddingConfig {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private String baseUrl;

  private String modelName;

  private String encryptedApiKey;

  private Integer apiKeyEncryptionVersion;

  private String apiKeyMasked;

  private Integer dimension;

  private Integer rowVersion;

  @TableField(fill = FieldFill.INSERT)
  private Instant createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
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

  public Integer getDimension() {
    return dimension;
  }

  public void setDimension(Integer dimension) {
    this.dimension = dimension;
  }

  public Integer getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Integer rowVersion) {
    this.rowVersion = rowVersion;
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
}
