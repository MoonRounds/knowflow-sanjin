package knowflow.sanjin.modules.processing.service;

import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务提交：在业务事务内写入 PENDING ProcessingTask（兼轻量 Outbox），提交后经 Publisher 投递。
 *
 * <p>活动状态唯一约束由 {@code (task_type, business_key, active_flag)} 生成列保证；重复提交同一 business_key 的活跃任务会触发
 * DuplicateKeyException。发布失败不影响业务提交，由恢复扫描兜底。
 */
@Service
public class TaskSubmissionService {

  private final ProcessingTaskMapper mapper;
  private final TaskPublisher publisher;

  public TaskSubmissionService(ProcessingTaskMapper mapper, TaskPublisher publisher) {
    this.mapper = mapper;
    this.publisher = publisher;
  }

  /** 在当前（外层）事务内插入 PENDING 任务并在提交后发布。 */
  @Transactional
  public ProcessingTask submit(
      String taskType,
      String businessKey,
      Long businessId,
      long ownerId,
      String payload,
      int maxRetries) {
    ProcessingTask task = new ProcessingTask();
    task.setTaskType(taskType);
    task.setBusinessKey(businessKey);
    task.setBusinessId(businessId);
    task.setOwnerId(ownerId);
    task.setStatus(ProcessingConstants.STATUS_PENDING);
    task.setRetryCount(0);
    task.setMaxRetries(maxRetries);
    task.setAttemptedDeliveries(0);
    task.setPayload(payload);
    mapper.insert(task);
    publisher.publishAfterCommit(task.getId());
    return task;
  }
}
