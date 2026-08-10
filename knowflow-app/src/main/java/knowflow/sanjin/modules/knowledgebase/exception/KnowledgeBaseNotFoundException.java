package knowflow.sanjin.modules.knowledgebase.exception;

/** 资源不存在或属于其他 Owner（越权视为不存在，不区分以免信息泄露）。 */
public class KnowledgeBaseNotFoundException extends RuntimeException {

  private final Long id;

  public KnowledgeBaseNotFoundException(Long id) {
    super("KnowledgeBase not found: id=" + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
