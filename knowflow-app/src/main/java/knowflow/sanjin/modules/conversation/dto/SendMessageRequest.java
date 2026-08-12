package knowflow.sanjin.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发送消息请求：clientMessageId 用于幂等去重，modelConfigId 可选（缺省用 Conversation/Owner 默认）。 */
public class SendMessageRequest {

  @NotBlank
  @Size(max = 64)
  private String clientMessageId;

  @NotBlank private String content;

  private String modelConfigId;

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

  public String getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(String modelConfigId) {
    this.modelConfigId = modelConfigId;
  }
}
