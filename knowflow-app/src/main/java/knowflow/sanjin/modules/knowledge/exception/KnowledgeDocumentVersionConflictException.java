package knowflow.sanjin.modules.knowledge.exception;

/** KnowledgeDocument 乐观锁版本冲突（If-Match/ETag 版本过期）。 */
public class KnowledgeDocumentVersionConflictException extends RuntimeException {

  public KnowledgeDocumentVersionConflictException() {
    super("知识文档已被其他请求修改，请刷新后重试");
  }
}
