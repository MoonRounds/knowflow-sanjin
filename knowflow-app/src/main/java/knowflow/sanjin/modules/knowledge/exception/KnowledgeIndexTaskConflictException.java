package knowflow.sanjin.modules.knowledge.exception;

/**
 * 同一 Item 同一 contentVersion 的索引任务仍在活动（PENDING/PROCESSING），拒绝重复提交。 状态已 FAILED 时允许通过 Processing
 * 页面手动重试。
 */
public class KnowledgeIndexTaskConflictException extends RuntimeException {

  public KnowledgeIndexTaskConflictException(Long itemId, int contentVersion) {
    super(
        "An active index task already exists for KnowledgeItem "
            + itemId
            + " contentVersion "
            + contentVersion);
  }
}
