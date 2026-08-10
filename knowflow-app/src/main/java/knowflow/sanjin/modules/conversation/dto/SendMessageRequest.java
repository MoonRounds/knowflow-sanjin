package knowflow.sanjin.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendMessageRequest {

  @NotBlank
  @Size(max = 64)
  private String clientMessageId;

  @NotBlank private String content;

  private Long modelConfigId;

  public String getClientMessageId() {
    return clientMessageId;
  }

  public void setClientMessageId(String clientMessageId) {
    this.clientMessageId = clientMessageId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Long getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(Long modelConfigId) {
    this.modelConfigId = modelConfigId;
  }
}
