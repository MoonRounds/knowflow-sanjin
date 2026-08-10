package knowflow.sanjin.modules.conversation.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 会话：Owner 下的聊天会话，保存默认模型与 active generation 指针。 */
@TableName("conversation")
public class Conversation {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ownerId;

  private String title;

  private Long defaultModelConfigId;

  private Long activeGenerationMessageId;

  private Boolean deleted;

  @Version private Integer rowVersion;

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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Long getDefaultModelConfigId() {
    return defaultModelConfigId;
  }

  public void setDefaultModelConfigId(Long defaultModelConfigId) {
    this.defaultModelConfigId = defaultModelConfigId;
  }

  public Long getActiveGenerationMessageId() {
    return activeGenerationMessageId;
  }

  public void setActiveGenerationMessageId(Long activeGenerationMessageId) {
    this.activeGenerationMessageId = activeGenerationMessageId;
  }

  public Boolean getDeleted() {
    return deleted;
  }

  public void setDeleted(Boolean deleted) {
    this.deleted = deleted;
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
