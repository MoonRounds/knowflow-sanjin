package knowflow.sanjin.modules.processing.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
    return submitTask(businessKey, 3);
  }

  private ProcessingTask submitTask(String businessKey, int maxRetries) {
    return submissionService.submit(
        ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX, businessKey, 42L, 1L, null, maxRetries);
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
    assertThat(updated.getRetryCount()).isEqualTo(updated.getMaxRetries());

    // 最终失败消息应在 DLQ 中，且消息 taskId 与 MySQL FAILED 行一致。
    Object dlqMessage = getDlqMessage(task.getId());
    assertThat(dlqMessage).isNotNull();
  }

  @Test
  @DisplayName("should persist QDRANT_UNAVAILABLE and route the same task to DLQ")
  void shouldFailAndDlqOnQdrantFailure() throws Exception {
    doThrow(new RetryableIndexException(ErrorCode.QDRANT_UNAVAILABLE, "qdrant down", null))
        .when(indexingService)
        .execute(any(ProcessingTask.class));

    ProcessingTask task = submitTask("qdrant-fail", 0);

    waitForStatus(task.getId(), ProcessingConstants.STATUS_FAILED);
    ProcessingTask updated = taskMapper.selectById(task.getId());
    assertThat(updated.getFailureCode()).isEqualTo(ErrorCode.QDRANT_UNAVAILABLE);
    assertThat(getDlqMessage(task.getId())).isNotNull();
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

    Object dlqMessage = getDlqMessage(task.getId());
    assertThat(dlqMessage).isNotNull();
  }

  @Test
  @DisplayName(
      "should ignore duplicate delivery idempotently (claim already in PROCESSING/SUCCEEDED)")
  void shouldIgnoreDuplicateDelivery() throws Exception {
    CountDownLatch firstCall = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    doAnswer(
            invocation -> {
              calls.incrementAndGet();
              firstCall.countDown();
              return null;
            })
        .when(indexingService)
        .execute(any(ProcessingTask.class));

    ProcessingTask task = submitTask("duplicate-delivery");
    // 第一次消费完成（SUCCEEDED）
    waitForStatus(task.getId(), ProcessingConstants.STATUS_SUCCEEDED);
    assertThat(firstCall.getCount()).isEqualTo(0);
    assertThat(calls.get()).isEqualTo(1);

    // 重复发布同一条消息：claim 返回 null（已终态）→ ack 忽略，不再执行
    publisher.publishAfterCommit(task.getId());

    // 轮询短窗口确认未再次执行
    long deadline = System.currentTimeMillis() + 3000;
    while (System.currentTimeMillis() < deadline) {
      if (calls.get() > 1) {
        break;
      }
      Thread.sleep(100);
    }
    assertThat(calls.get()).isEqualTo(1);
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

  private Object getDlqMessage(Long expectedTaskId) {
    // 使用正式 MessageConverter 读取；原 taskId 字符串在线上以 JSON 消息编码，不比较 raw bytes。
    for (int i = 0; i < 30; i++) {
      Object payload = rabbitTemplate.receiveAndConvert(rabbitProperties.dlqName(), 500);
      if (payload != null && expectedTaskId.toString().equals(String.valueOf(payload))) {
        return payload;
      }
    }
    return null;
  }
}
