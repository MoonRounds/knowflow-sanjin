package knowflow.sanjin.modules.conversation.exception;

/** Conversation 乐观锁冲突。 */
public class ConversationVersionConflictException extends RuntimeException {

  public ConversationVersionConflictException() {
    super("会话已被其他请求修改，请刷新后重试。");
  }
}
