package knowflow.sanjin.modules.conversation.exception;

/** 同一 Conversation 已存在一个 active Generation，拒绝新的并发生成。 */
public class ActiveGenerationExistsException extends RuntimeException {

  public ActiveGenerationExistsException(Long conversationId) {
    super("会话 " + conversationId + " 已存在进行中的生成");
  }
}
