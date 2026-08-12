package knowflow.sanjin.modules.conversation.title;

import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskDomainRecovery;
import org.springframework.stereotype.Component;

/**
 * 会话标题任务域回写边界：标题直接落在 conversation 表，无独立投影状态需同步；但恢复扫描重发时必须 路由到会话标题工作队列（而非默认索引队列），因此提供 {@code
 * queueBase}。
 */
@Component
public class ConversationTitleTaskRecovery implements ProcessingTaskDomainRecovery {

  @Override
  public boolean supports(ProcessingTask task) {
    return ProcessingConstants.TASK_TYPE_CONVERSATION_TITLE.equals(task.getTaskType());
  }

  @Override
  public String queueBase() {
    return ConversationTitleService.WORK_QUEUE_BASE;
  }

  @Override
  public void prepareForRepublish(ProcessingTask task) {
    // 标题以 ProcessingTask 为事实源，无需额外领域状态回写。
  }
}
