package knowflow.sanjin.modules.conversation.dto;

import jakarta.validation.constraints.Size;

public class UpdateConversationRequest {

  @Size(max = 200)
  private String title;

  private Long defaultModelConfigId;

  private Long rowVersion;

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

  public Long getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Long rowVersion) {
    this.rowVersion = rowVersion;
  }
}
