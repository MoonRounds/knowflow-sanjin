package knowflow.sanjin.modules.conversation.vo;

import java.time.Instant;

public class ConversationResponse {

  private String id;
  private String title;
  private String defaultModelConfigId;
  private String activeGenerationMessageId;
  private Integer rowVersion;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDefaultModelConfigId() {
    return defaultModelConfigId;
  }

  public void setDefaultModelConfigId(String defaultModelConfigId) {
    this.defaultModelConfigId = defaultModelConfigId;
  }

  public String getActiveGenerationMessageId() {
    return activeGenerationMessageId;
  }

  public void setActiveGenerationMessageId(String activeGenerationMessageId) {
    this.activeGenerationMessageId = activeGenerationMessageId;
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
