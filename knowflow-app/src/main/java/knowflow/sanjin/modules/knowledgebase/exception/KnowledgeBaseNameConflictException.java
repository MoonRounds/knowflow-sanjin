package knowflow.sanjin.modules.knowledgebase.exception;

public class KnowledgeBaseNameConflictException extends RuntimeException {

  private final String name;

  public KnowledgeBaseNameConflictException(String name) {
    super("KnowledgeBase with this name already exists: " + name);
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
