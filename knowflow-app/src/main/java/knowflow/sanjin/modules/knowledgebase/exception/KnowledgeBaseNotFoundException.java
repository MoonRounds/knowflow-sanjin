package knowflow.sanjin.modules.knowledgebase.exception;

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
