package knowflow.sanjin.modules.knowledge.exception;

/** 引用了一个不存在或不可访问的 KnowledgeBase（owner 越权视为不存在）。 */
public class KnowledgeBaseRefNotFoundException extends RuntimeException {

  private final Long knowledgeBaseId;

  public KnowledgeBaseRefNotFoundException(Long knowledgeBaseId) {
    super("Referenced KnowledgeBase not found or not accessible: " + knowledgeBaseId);
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public Long getKnowledgeBaseId() {
    return knowledgeBaseId;
  }
}
