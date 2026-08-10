package knowflow.sanjin.modules.processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 恢复扫描集成测试：recover() 必须重发未投递（last_delivery_at 为空）与投递后滞留超时（PENDING 且 last_delivery_at 过期）的 PENDING
 * 任务，并恢复租约超时的 PROCESSING 任务；lease 内仍新鲜的 PENDING 任务不得误发。
 */
@SpringBootTest
@DisplayName("ProcessingTaskService recovery Integration Tests")
class ProcessingTaskServiceRecoveryIT extends MySQLTestBase {

  @Autowired private ProcessingTaskMapper taskMapper;

  @Autowired private ProcessingTaskService taskService;

  @MockitoBean private TaskPublisher publisher;

  private ProcessingTask insertTask(
      String businessKey, String status, int attempted, Instant lastDeliveryAt) {
    ProcessingTask task = new ProcessingTask();
    task.setOwnerId(1L);
    task.setTaskType(ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX);
    task.setBusinessKey(businessKey);
    task.setBusinessId(42L);
    task.setStatus(status);
    task.setRetryCount(0);
    task.setMaxRetries(3);
    task.setAttemptedDeliveries(attempted);
    task.setLastDeliveryAt(lastDeliveryAt);
    taskMapper.insert(task);
    return task;
  }

  @Test
  @DisplayName("should republish an undelivered PENDING task (last_delivery_at null)")
  void shouldRepublishUndeliveredPending() {
    ProcessingTask task =
        insertTask("recover-undelivered", ProcessingConstants.STATUS_PENDING, 0, null);

    int count = taskService.recover();

    assertThat(count).isGreaterThanOrEqualTo(1);
    verify(publisher).publishAfterCommit(task.getId());
  }

  @Test
  @DisplayName("should republish a PENDING task that was delivered but stranded past lease")
  void shouldRepublishStrandedPending() {
    // claim 曾成功（attempted>0、last_delivery_at 记录），但消息滞留、任务仍 PENDING（如 retry 消息丢失）
    ProcessingTask task =
        insertTask(
            "recover-stranded",
            ProcessingConstants.STATUS_PENDING,
            2,
            Instant.now().minus(Duration.ofHours(2)));

    int count = taskService.recover();

    assertThat(count).isGreaterThanOrEqualTo(1);
    verify(publisher).publishAfterCommit(task.getId());
  }

  @Test
  @DisplayName("should NOT republish a PENDING task with fresh last_delivery_at within lease")
  void shouldNotRepublishFreshPending() {
    // 仍在 retry 队列等待期内（lease 默认 10m，这里 last_delivery_at 为 1 分钟前）不得提前重发
    ProcessingTask task =
        insertTask(
            "recover-fresh",
            ProcessingConstants.STATUS_PENDING,
            1,
            Instant.now().minus(Duration.ofMinutes(1)));

    int count = taskService.recover();

    // 仅当列表里有其他可恢复任务时才 >0；fresh 任务本身不得被重发
    verify(publisher, never()).publishAfterCommit(task.getId());
    assertThat(taskMapper.selectById(task.getId()).getStatus())
        .isEqualTo(ProcessingConstants.STATUS_PENDING);
  }

  @Test
  @DisplayName("should recover a stuck PROCESSING task past its lease")
  void shouldRecoverStuckProcessing() {
    ProcessingTask stuck =
        insertTask(
            "recover-stuck-processing", ProcessingConstants.STATUS_PROCESSING, 1, Instant.now());
    // 把 updated_at 改为租约外，模拟 Consumer 崩溃后任务卡死
    taskMapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProcessingTask>()
            .eq(ProcessingTask::getId, stuck.getId())
            .set(ProcessingTask::getUpdatedAt, Instant.now().minus(Duration.ofHours(2))));

    int count = taskService.recover();

    assertThat(count).isGreaterThanOrEqualTo(1);
    assertThat(taskMapper.selectById(stuck.getId()).getStatus())
        .isEqualTo(ProcessingConstants.STATUS_PENDING);
    verify(publisher).publishAfterCommit(stuck.getId());
  }
}
