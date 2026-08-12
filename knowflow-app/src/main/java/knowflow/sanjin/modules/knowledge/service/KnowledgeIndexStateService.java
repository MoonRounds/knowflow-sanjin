package knowflow.sanjin.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 把完整索引任务状态投影到 KnowledgeItem；payload 更新与删除任务不改变正文索引状态。 */
@Service
public class KnowledgeIndexStateService {

  private final KnowledgeItemMapper itemMapper;

  public KnowledgeIndexStateService(KnowledgeItemMapper itemMapper) {
    this.itemMapper = itemMapper;
  }

  @Transactional
  public void markProcessing(ProcessingTask task) {
    update(task, KnowledgeConstants.INDEX_PROCESSING, null, null);
  }

  @Transactional
  public void markPending(ProcessingTask task) {
    update(task, KnowledgeConstants.INDEX_PENDING, null, null);
  }

  @Transactional
  public void markFailed(ProcessingTask task, String failureCode, String lastError) {
    update(task, KnowledgeConstants.INDEX_FAILED, failureCode, trim(lastError));
  }

  private void update(ProcessingTask task, String status, String failureCode, String errorMessage) {
    if (!isFullIndex(task)) {
      return;
    }
    Integer taskVersion = taskContentVersion(task.getBusinessKey());
    if (taskVersion == null) {
      return;
    }
    itemMapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeItem>()
            .eq(KnowledgeItem::getId, task.getBusinessId())
            .eq(KnowledgeItem::getOwnerId, task.getOwnerId())
            .eq(KnowledgeItem::getStatus, KnowledgeConstants.STATUS_ACTIVE)
            .eq(KnowledgeItem::getContentVersion, taskVersion)
            .set(KnowledgeItem::getIndexStatus, status)
            .set(KnowledgeItem::getIndexErrorCode, failureCode)
            .set(KnowledgeItem::getIndexErrorMessage, errorMessage));
  }

  private static boolean isFullIndex(ProcessingTask task) {
    return ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX.equals(task.getTaskType())
        && task.getBusinessKey() != null
        && !task.getBusinessKey().endsWith(KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX);
  }

  private static Integer taskContentVersion(String businessKey) {
    String[] parts = businessKey.split(KnowledgeConstants.BUSINESS_KEY_DELIMITER);
    if (parts.length < 3) {
      return null;
    }
    try {
      return Integer.valueOf(parts[parts.length - 1]);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static String trim(String value) {
    if (value == null || value.length() <= 2_000) {
      return value;
    }
    return value.substring(0, 2_000);
  }
}
