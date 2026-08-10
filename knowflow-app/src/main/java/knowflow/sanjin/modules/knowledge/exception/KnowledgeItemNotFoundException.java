package knowflow.sanjin.modules.knowledge.exception;

/** KnowledgeItem 不存在或不可访问（owner 越权视为不存在）。 */
public class KnowledgeItemNotFoundException extends RuntimeException {

  private final Long id;

  public KnowledgeItemNotFoundException(Long id) {
    super("KnowledgeItem not found: " + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
