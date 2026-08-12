package knowflow.sanjin.modules.conversation.exception;

/** 消息不存在或不属于该会话/Owner。 */
public class MessageNotFoundException extends RuntimeException {

  private final Long conversationId;
  private final Long messageId;

  public MessageNotFoundException(Long conversationId, Long messageId) {
    super("消息不存在: conversationId=" + conversationId + ", messageId=" + messageId);
    this.conversationId = conversationId;
    this.messageId = messageId;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public Long getMessageId() {
    return messageId;
  }
}
