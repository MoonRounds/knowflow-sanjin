package knowflow.sanjin.modules.knowledgebase.exception;

/** 乐观锁版本冲突：If-Match/rowVersion 与当前行不一致，要求客户端刷新后重试。 */
public class KnowledgeBaseVersionConflictException extends RuntimeException {

  public KnowledgeBaseVersionConflictException() {
    super("KnowledgeBase has been modified by another request. Please refresh and try again.");
  }
}
