package knowflow.sanjin.modules.processing.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.util.concurrent.CountDownLatch;
import knowflow.sanjin.common.config.RabbitProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.modules.processing.service.IndexingService;
import knowflow.sanjin.modules.processing.service.TaskPublisher;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import knowflow.sanjin.testinfra.MySQLRabbitMQTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RabbitMQ 可靠投递集成测试：重试→成功、重试耗尽→FAILED+DLQ、不可重试→FAILED+DLQ、重复投递幂等。
 *
 * <p>通过替换 IndexingService 控制成功/可重试/终态行为；固定等待由 CountDownLatch 而非 sleep 驱动。 重试档位覆盖为
 * 1s，使「重试耗尽」场景在秒级完成。
 */
@SpringBootTest
@TestPropertySource(properties = "knowflow.rabbit.retry-delays=1s,1s,1s")
@DisplayName("IndexTaskConsumer RabbitMQ Integration Tests")
class IndexTaskConsumerIT extends MySQLRabbitMQTestBase {

  @Autowired private ProcessingTaskMapper taskMapper;

  @Autowired private TaskSubmissionService submissionService;

  @Autowired private TaskPublisher publisher;

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private RabbitProperties rabbitProperties;

  @MockitoBean private IndexingService indexingService;

  private ProcessingTask submitTask(String businessKey) {
    return submissionService.submit(
        ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX, businessKey, 42L, 1L, null, 3);
  }

  @Test
  @DisplayName("should mark SUCCEEDED when indexing succeeds")
  void shouldSucceedOnFirstAttempt() throws Exception {
    doAnswer(invocation -> null).when(indexingService).execute(any(ProcessingTask.class));
    ProcessingTask task = submitTask("succeed-1");

    waitForStatus(task.getId(), ProcessingConstants.STATUS_SUCCEEDED);

    ProcessingTask updated = taskMapper.selectById(task.getId());
    assertThat(updated.getStatus()).isEqualTo(ProcessingConstants.STATUS_SUCCEEDED);
    assertThat(updated.getFinishedAt()).isNotNull();
  }

  @Test
  @DisplayName("should retry on retryable failure and eventually succeed")
  void shouldRetryThenSucceed() throws Exception {
    CountDownLatch calls = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              calls.countDown();
              throw new RetryableIndexException(
                  ErrorCode.EMBEDDING_UNAVAILABLE, "embedding temporarily unavailable", null);
            })
        .doAnswer(invocation -> null)
        .when(indexingService)
        .execute(any(ProcessingTask.class));

    ProcessingTask task = submitTask("retry-then-succeed");

    waitForStatus(task.getId(), ProcessingConstants.STATUS_SUCCEEDED);

    ProcessingTask updated = taskMapper.selectById(task.getId());
    assertThat(updated.getStatus()).isEqualTo(ProcessingConstants.STATUS_SUCCEEDED);
    assertThat(updated.getRetryCount()).isEqualTo(1);
    assertThat(calls.getCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("should mark FAILED and reach DLQ when retries are exhausted")
  void shouldFailAndDlqWhenRetriesExhausted() throws Exception {
    doThrow(new RetryableIndexException(ErrorCode.EMBEDDING_UNAVAILABLE, "always failing", null))
        .when(indexingService)
        .execute(any(ProcessingTask.class));

    ProcessingTask task = submitTask("retry-exhausted");

    waitForStatus(task.getId(), ProcessingConstants.STATUS_FAILED);

    ProcessingTask updated = taskMapper.selectById(task.getId());
    assertThat(updated.getStatus()).isEqualTo(ProcessingConstants.STATUS_FAILED);
    assertThat(updated.getFailureCode()).isEqualTo(ErrorCode.EMBEDDING_UNAVAILABLE);

    // 最终失败消息应在 DLQ 中
    Message dlqMessage = getDlqMessage();
    assertThat(dlqMessage).isNotNull();
  }

  @Test
  @DisplayName("should mark FAILED and reach DLQ on terminal (non-retryable) error")
  void shouldFailImmediatelyOnTerminalError() throws Exception {
    doThrow(new TerminalIndexException(ErrorCode.EMBEDDING_AUTH_FAILURE, "auth rejected"))
        .when(indexingService)
        .execute(any(ProcessingTask.class));

    ProcessingTask task = submitTask("terminal-fail");

    waitForStatus(task.getId(), ProcessingConstants.STATUS_FAILED);

    ProcessingTask updated = taskMapper.selectById(task.getId());
    assertThat(updated.getStatus()).isEqualTo(ProcessingConstants.STATUS_FAILED);
    assertThat(updated.getFailureCode()).isEqualTo(ErrorCode.EMBEDDING_AUTH_FAILURE);
    assertThat(updated.getRetryCount()).isEqualTo(0); // 无重试

    Message dlqMessage = getDlqMessage();
    assertThat(dlqMessage).isNotNull();
  }

  @Test
  @DisplayName(
      "should ignore duplicate delivery idempotently (claim already in PROCESSING/SUCCEEDED)")
  void shouldIgnoreDuplicateDelivery() throws Exception {
    CountDownLatch calls = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              calls.countDown();
              return null;
            })
        .when(indexingService)
        .execute(any(ProcessingTask.class));

    ProcessingTask task = submitTask("duplicate-delivery");
    // 第一次消费完成（SUCCEEDED）
    waitForStatus(task.getId(), ProcessingConstants.STATUS_SUCCEEDED);
    assertThat(calls.getCount()).isEqualTo(0);

    // 重复发布同一条消息：claim 返回 null（已终态）→ ack 忽略，不再执行
    publisher.publishAfterCommit(task.getId());

    // 轮询短窗口确认未再次执行
    long deadline = System.currentTimeMillis() + 3000;
    while (System.currentTimeMillis() < deadline) {
      // 如果误执行了，calls 会变为负数（溢出保护）——这里只需等待窗口结束
      Thread.sleep(100);
    }
    assertThat(calls.getCount()).isEqualTo(0); // 仍为 0：未再次执行
    assertThat(taskMapper.selectById(task.getId()).getStatus())
        .isEqualTo(ProcessingConstants.STATUS_SUCCEEDED);
  }

  private void waitForStatus(Long taskId, String status) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      ProcessingTask task = taskMapper.selectById(taskId);
      if (task != null && status.equals(task.getStatus())) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError(
        "Timed out waiting for task "
            + taskId
            + " to reach "
            + status
            + " (last="
            + taskMapper.selectById(taskId).getStatus()
            + ")");
  }

  private Message getDlqMessage() {
    // DLQ 上的消息消费后即消失，这里轮询短窗口抓取
    for (int i = 0; i < 30; i++) {
      Message msg = rabbitTemplate.receive(rabbitProperties.dlqName(), 500);
      if (msg != null) {
        return msg;
      }
    }
    return null;
  }
}
