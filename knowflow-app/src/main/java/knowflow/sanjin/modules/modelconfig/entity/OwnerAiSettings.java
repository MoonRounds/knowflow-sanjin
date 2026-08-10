package knowflow.sanjin.modules.modelconfig.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** Owner AI Settings：默认 Chat Model 与 Utility Model 的配置引用。 */
@TableName("owner_ai_settings")
public class OwnerAiSettings {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private Long defaultChatModelConfigId;

  private Long utilityModelConfigId;

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

  public Long getDefaultChatModelConfigId() {
    return defaultChatModelConfigId;
  }

  public void setDefaultChatModelConfigId(Long defaultChatModelConfigId) {
    this.defaultChatModelConfigId = defaultChatModelConfigId;
  }

  public Long getUtilityModelConfigId() {
    return utilityModelConfigId;
  }

  public void setUtilityModelConfigId(Long utilityModelConfigId) {
    this.utilityModelConfigId = utilityModelConfigId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
