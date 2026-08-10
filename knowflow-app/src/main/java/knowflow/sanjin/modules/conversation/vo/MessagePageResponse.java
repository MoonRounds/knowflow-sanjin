package knowflow.sanjin.modules.conversation.vo;

import java.util.List;

/** 消息历史分页响应：带下一页游标（更早消息的 before 值）。 */
public class MessagePageResponse {

  private List<MessageResponse> messages;
  private String nextBefore;

  public List<MessageResponse> getMessages() {
    return messages;
  }

  public void setMessages(List<MessageResponse> messages) {
    this.messages = messages;
  }

  public String getNextBefore() {
    return nextBefore;
  }

  public void setNextBefore(String nextBefore) {
    this.nextBefore = nextBefore;
  }
}
