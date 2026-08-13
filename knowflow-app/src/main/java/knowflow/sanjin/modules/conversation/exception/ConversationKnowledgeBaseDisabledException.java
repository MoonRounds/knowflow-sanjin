package knowflow.sanjin.modules.conversation.exception;

/** 新绑定引用了已禁用的知识库。 */
public class ConversationKnowledgeBaseDisabledException extends RuntimeException {

  private final Long knowledgeBaseId;

  public ConversationKnowledgeBaseDisabledException(Long knowledgeBaseId) {
    super("知识库 id=" + knowledgeBaseId + " 已禁用，不能绑定到会话。");
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public Long getKnowledgeBaseId() {
    return knowledgeBaseId;
  }
}
