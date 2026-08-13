package knowflow.sanjin.modules.conversation.exception;

/** 会话仍有非终态提取任务（PENDING/PROCESSING），拒绝硬删除，避免消费端与删除并发竞态。 */
public class ConversationExtractionInProgressException extends RuntimeException {

  public ConversationExtractionInProgressException(Long conversationId) {
    super("会话 " + conversationId + " 正在提取知识，请稍后删除");
  }
}
