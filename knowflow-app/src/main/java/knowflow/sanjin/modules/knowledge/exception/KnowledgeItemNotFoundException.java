package knowflow.sanjin.modules.knowledge.exception;

/** KnowledgeItem 不存在或不可访问（owner 越权视为不存在）。 */
public class KnowledgeItemNotFoundException extends RuntimeException {

  private final Long id;

  public KnowledgeItemNotFoundException(Long id) {
    super("知识条目不存在: " + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
