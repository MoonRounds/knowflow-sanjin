package knowflow.sanjin.modules.modelconfig.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** ModelConfig 逻辑配置：指向当前生效的 Revision。 */
@TableName("model_config")
public class ModelConfig {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private String displayName;

  private String providerName;

  private Boolean enabled;

  private Boolean deleted;

  private Long currentRevisionId;

  private Long utilityTestedRevisionId;

  private Boolean utilityRouterSchemaValid;

  private Boolean utilityCandidateSchemaValid;

  private Instant utilityCapabilityTestedAt;

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

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
  }

  public Long getCurrentRevisionId() {
    return currentRevisionId;
  }

  public void setCurrentRevisionId(Long currentRevisionId) {
    this.currentRevisionId = currentRevisionId;
  }

  public Long getUtilityTestedRevisionId() {
    return utilityTestedRevisionId;
  }

  public void setUtilityTestedRevisionId(Long utilityTestedRevisionId) {
    this.utilityTestedRevisionId = utilityTestedRevisionId;
  }

  public Boolean getUtilityRouterSchemaValid() {
    return utilityRouterSchemaValid;
  }

  public void setUtilityRouterSchemaValid(Boolean utilityRouterSchemaValid) {
    this.utilityRouterSchemaValid = utilityRouterSchemaValid;
  }

  public Boolean getUtilityCandidateSchemaValid() {
    return utilityCandidateSchemaValid;
  }

  public void setUtilityCandidateSchemaValid(Boolean utilityCandidateSchemaValid) {
    this.utilityCandidateSchemaValid = utilityCandidateSchemaValid;
  }

  public Instant getUtilityCapabilityTestedAt() {
    return utilityCapabilityTestedAt;
  }

  public void setUtilityCapabilityTestedAt(Instant utilityCapabilityTestedAt) {
    this.utilityCapabilityTestedAt = utilityCapabilityTestedAt;
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
