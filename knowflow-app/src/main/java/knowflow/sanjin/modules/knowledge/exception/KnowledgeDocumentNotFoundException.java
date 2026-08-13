package knowflow.sanjin.modules.knowledge.exception;

/** KnowledgeDocument 不存在或不可访问（owner 越权视为不存在）。 */
public class KnowledgeDocumentNotFoundException extends RuntimeException {

  private final Long id;

  public KnowledgeDocumentNotFoundException(Long id) {
    super("知识文档不存在: " + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
