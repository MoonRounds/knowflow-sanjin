package knowflow.sanjin.modules.conversation.exception;

public class MessageNotFoundException extends RuntimeException {

  private final Long conversationId;
  private final Long messageId;

  public MessageNotFoundException(Long conversationId, Long messageId) {
    super("Message not found: conversationId=" + conversationId + ", messageId=" + messageId);
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
