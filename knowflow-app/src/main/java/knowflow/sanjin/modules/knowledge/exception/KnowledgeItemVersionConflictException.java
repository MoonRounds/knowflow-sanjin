package knowflow.sanjin.modules.knowledge.exception;

/** KnowledgeItem 乐观锁版本冲突（If-Match/ETag 版本过期）。 */
public class KnowledgeItemVersionConflictException extends RuntimeException {

  public KnowledgeItemVersionConflictException() {
    super("知识条目已被其他请求修改，请刷新后重试");
  }
}
