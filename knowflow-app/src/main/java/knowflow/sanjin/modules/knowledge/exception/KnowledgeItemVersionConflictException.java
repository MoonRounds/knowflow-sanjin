package knowflow.sanjin.modules.knowledge.exception;

/** KnowledgeItem 乐观锁版本冲突（If-Match/ETag 版本过期）。 */
public class KnowledgeItemVersionConflictException extends RuntimeException {

  public KnowledgeItemVersionConflictException() {
    super("KnowledgeItem has been modified by another request");
  }
}
