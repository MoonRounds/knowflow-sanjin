package knowflow.sanjin.modules.knowledgebase.exception;

public class KnowledgeBaseVersionConflictException extends RuntimeException {

  public KnowledgeBaseVersionConflictException() {
    super("KnowledgeBase has been modified by another request. Please refresh and try again.");
  }
}
