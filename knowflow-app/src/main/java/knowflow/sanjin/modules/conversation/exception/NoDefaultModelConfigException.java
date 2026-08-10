package knowflow.sanjin.modules.conversation.exception;

/** 发送消息时 Conversation 与 Owner 都没有可用的默认 Chat Model。 */
public class NoDefaultModelConfigException extends RuntimeException {

  public NoDefaultModelConfigException(Long conversationId) {
    super("No default chat model configured for conversation " + conversationId);
  }
}
