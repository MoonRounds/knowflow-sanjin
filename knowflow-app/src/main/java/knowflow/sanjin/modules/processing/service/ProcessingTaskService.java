package knowflow.sanjin.modules.processing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import knowflow.sanjin.common.config.RabbitProperties;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.exception.ProcessingTaskNotFoundException;
import knowflow.sanjin.modules.processing.exception.ProcessingTaskRetryNotAllowedException;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProcessingTask 状态机与恢复：claim（PENDING→PROCESSING）、成功/失败终态、PROCESSING 租约超时恢复、 未投递任务补发、手动 Retry。
 *
 * <p>幂等：状态条件更新（目标状态匹配才生效）保证并发 Consumer 只有一个赢得 claim；重复成功/失败更新 不会回退状态。手动 Retry 复用同一
 * businessKey，由活动状态唯一约束去重并发点击。
 */
@Service
public class ProcessingTaskService {

  private static final Logger log = LoggerFactory.getLogger(ProcessingTaskService.class);

  private final ProcessingTaskMapper mapper;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final TaskPublisher publisher;
  private final RabbitProperties properties;

  public ProcessingTaskService(
      ProcessingTaskMapper mapper,
      CurrentOwnerProvider currentOwnerProvider,
      TaskPublisher publisher,
      RabbitProperties properties) {
    this.mapper = mapper;
    this.currentOwnerProvider = currentOwnerProvider;
    this.publisher = publisher;
    this.properties = properties;
  }

  /** 认领任务：PENDING → PROCESSING，并记录投递；并发下只有一个成功。返回 null 表示已被并发认领或不存在。 */
  @Transactional
  public ProcessingTask claim(Long taskId) {
    LambdaUpdateWrapper<ProcessingTask> update =
        new LambdaUpdateWrapper<ProcessingTask>()
            .eq(ProcessingTask::getId, taskId)
            .eq(ProcessingTask::getStatus, ProcessingConstants.STATUS_PENDING)
            .set(ProcessingTask::getStatus, ProcessingConstants.STATUS_PROCESSING)
            .set(ProcessingTask::getStartedAt, Instant.now())
            .setSql("attempted_deliveries = attempted_deliveries + 1")
            .set(ProcessingTask::getLastDeliveryAt, Instant.now());
    if (mapper.update(null, update) != 1) {
      return null;
    }
    return mapper.selectById(taskId);
  }

  /** 标记成功：只有 PROCESSING/PENDING 状态可进入 SUCCEEDED，幂等。 */
  @Transactional
  public boolean markSucceeded(Long taskId) {
    LambdaUpdateWrapper<ProcessingTask> update =
        new LambdaUpdateWrapper<ProcessingTask>()
            .eq(ProcessingTask::getId, taskId)
            .in(
                ProcessingTask::getStatus,
                ProcessingConstants.STATUS_PROCESSING,
                ProcessingConstants.STATUS_PENDING)
            .set(ProcessingTask::getStatus, ProcessingConstants.STATUS_SUCCEEDED)
            .set(ProcessingTask::getFinishedAt, Instant.now());
    return mapper.update(null, update) == 1;
  }

  /**
   * 可重试失败：retryCount &lt; maxRetries 时递增并保持 PENDING，返回新的 retryCount（&gt;0 表示可重试）； 否则视为最后一次尝试失败，直接
   * FAILED。
   */
  @Transactional
  public int failRetryable(Long taskId, String failureCode, String lastError) {
    ProcessingTask task = mapper.selectById(taskId);
    if (task == null || isTerminal(task)) {
      return 0;
    }
    if (task.getRetryCount() < task.getMaxRetries()) {
      int next = task.getRetryCount() + 1;
      mapper.update(
          null,
          new LambdaUpdateWrapper<ProcessingTask>()
              .eq(ProcessingTask::getId, taskId)
              .set(ProcessingTask::getStatus, ProcessingConstants.STATUS_PENDING)
              .set(ProcessingTask::getRetryCount, next)
              .set(ProcessingTask::getFailureCode, failureCode)
              .set(ProcessingTask::getLastError, lastError)
              .set(ProcessingTask::getStartedAt, null));
      return next;
    }
    mapper.update(
        null,
        new LambdaUpdateWrapper<ProcessingTask>()
            .eq(ProcessingTask::getId, taskId)
            .set(ProcessingTask::getStatus, ProcessingConstants.STATUS_FAILED)
            .set(ProcessingTask::getFailureCode, failureCode)
            .set(ProcessingTask::getLastError, lastError)
            .set(ProcessingTask::getFinishedAt, Instant.now()));
    return 0;
  }

  /** 不可重试失败：直接 FAILED。 */
  @Transactional
  public void failTerminal(Long taskId, String failureCode, String lastError) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<ProcessingTask>()
            .eq(ProcessingTask::getId, taskId)
            .in(
                ProcessingTask::getStatus,
                ProcessingConstants.STATUS_PROCESSING,
                ProcessingConstants.STATUS_PENDING)
            .set(ProcessingTask::getStatus, ProcessingConstants.STATUS_FAILED)
            .set(ProcessingTask::getFailureCode, failureCode)
            .set(ProcessingTask::getLastError, lastError)
            .set(ProcessingTask::getFinishedAt, Instant.now()));
  }

  /** 扫描并重新发布：未投递（从未投递或投递后滞留超时）的 PENDING 任务与卡死 PROCESSING 租约超时。 */
  @Transactional
  public int recover() {
    int republished = 0;
    Instant staleCutoff = Instant.now().minus(properties.getProcessingLeaseTimeout());
    // PENDING 未投递（last_delivery_at 为空）或投递过但滞留超时（claim 成功、ack 前崩溃回滚）都要重发
    List<ProcessingTask> undelivered =
        mapper.selectList(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getStatus, ProcessingConstants.STATUS_PENDING)
                .and(
                    w ->
                        w.isNull(ProcessingTask::getLastDeliveryAt)
                            .or()
                            .lt(ProcessingTask::getLastDeliveryAt, staleCutoff)));
    for (ProcessingTask task : undelivered) {
      republishToWork(task.getId());
      republished++;
    }

    int recovered = 0;
    List<ProcessingTask> stuck =
        mapper.selectList(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getStatus, ProcessingConstants.STATUS_PROCESSING)
                .lt(ProcessingTask::getUpdatedAt, staleCutoff));
    for (ProcessingTask task : stuck) {
      LambdaUpdateWrapper<ProcessingTask> update =
          new LambdaUpdateWrapper<ProcessingTask>()
              .eq(ProcessingTask::getId, task.getId())
              .eq(ProcessingTask::getStatus, ProcessingConstants.STATUS_PROCESSING)
              .set(ProcessingTask::getStatus, ProcessingConstants.STATUS_PENDING)
              .set(ProcessingTask::getStartedAt, null);
      if (mapper.update(null, update) == 1) {
        republishToWork(task.getId());
        recovered++;
      }
    }
    int total = republished + recovered;
    if (total > 0) {
      log.info("Recovery scan republished={} recovered={}", republished, recovered);
    }
    return total;
  }

  /** 手动 Retry：原任务 FAILED 后创建新任务（retryOfTaskId 关联），复用同一 businessKey；活动去重。 */
  @Transactional
  public ProcessingTask manualRetry(Long taskId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ProcessingTask original = getByIdAndOwner(taskId, ownerId);
    if (!ProcessingConstants.STATUS_FAILED.equals(original.getStatus())) {
      throw new ProcessingTaskRetryNotAllowedException(taskId, original.getStatus());
    }
    ProcessingTask retry = new ProcessingTask();
    retry.setOwnerId(ownerId);
    retry.setTaskType(original.getTaskType());
    retry.setBusinessKey(original.getBusinessKey());
    retry.setBusinessId(original.getBusinessId());
    retry.setStatus(ProcessingConstants.STATUS_PENDING);
    retry.setRetryCount(0);
    retry.setMaxRetries(original.getMaxRetries());
    retry.setRetryOfTaskId(original.getId());
    retry.setPayload(original.getPayload());
    retry.setAttemptedDeliveries(0);
    try {
      mapper.insert(retry);
    } catch (DuplicateKeyException e) {
      throw new ProcessingTaskRetryNotAllowedException(taskId, "ACTIVE_RETRY_EXISTS");
    }
    return retry;
  }

  /** 列表：owner 过滤，可选按状态过滤，按创建时间倒序。 */
  @Transactional(readOnly = true)
  public List<ProcessingTask> listForOwner(String status) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    LambdaQueryWrapper<ProcessingTask> wrapper =
        new LambdaQueryWrapper<ProcessingTask>().eq(ProcessingTask::getOwnerId, ownerId);
    if (status != null && !status.isBlank()) {
      wrapper.eq(ProcessingTask::getStatus, status.toUpperCase());
    }
    return mapper.selectList(wrapper.orderByDesc(ProcessingTask::getCreatedAt));
  }

  @Transactional(readOnly = true)
  public ProcessingTask getByIdAndOwner(Long taskId, long ownerId) {
    ProcessingTask task =
        mapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getId, taskId)
                .eq(ProcessingTask::getOwnerId, ownerId));
    if (task == null) {
      throw new ProcessingTaskNotFoundException(taskId);
    }
    return task;
  }

  private boolean isTerminal(ProcessingTask task) {
    return !ProcessingConstants.STATUS_PROCESSING.equals(task.getStatus())
        && !ProcessingConstants.STATUS_PENDING.equals(task.getStatus());
  }

  /** 直接投递到工作交换机（恢复扫描用）；发布失败静默留待下轮。 */
  private void republishToWork(Long taskId) {
    publisher.publishAfterCommit(taskId);
  }
}
