package knowflow.sanjin.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建会话请求：仅需标题（默认取首条消息截断，可后续改名）。 */
public class CreateConversationRequest {

  @NotBlank
  @Size(max = 200)
  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }
}
