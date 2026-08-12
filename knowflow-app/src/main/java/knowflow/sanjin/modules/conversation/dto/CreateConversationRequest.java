package knowflow.sanjin.modules.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。
 *
 * <p>标题可选：留空时由服务端用首条 User Message 安全截断兜底（无消息时为「新对话」占位，首轮回答后由 AI 自动生成，见 ADR 0004）。
 */
public class CreateConversationRequest {

  @Size(max = 200)
  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }
}
