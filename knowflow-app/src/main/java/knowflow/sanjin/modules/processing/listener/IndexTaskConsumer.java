package knowflow.sanjin.modules.processing.listener;

import com.rabbitmq.client.Channel;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.IndexingService;
import knowflow.sanjin.modules.processing.service.ProcessingTaskService;
import knowflow.sanjin.modules.processing.service.TaskPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 知识索引 Consumer：幂等消费 taskId 消息。
 *
 * <p>流程：claim（PENDING→PROCESSING，并发只胜一个）→ 执行索引 → 成功 markSucceeded；可重试失败递增 retryCount 并发布副本到 TTL
 * 重试队列、ack 原消息；重试耗尽或不可重试错误 markFailed 并 nack（requeue=false） 进入 DLQ。重复投递时 claim 失败直接 ack
 * 忽略，保证不制造重复向量。
 */
@Component
public class IndexTaskConsumer {

  private static final Logger log = LoggerFactory.getLogger(IndexTaskConsumer.class);

  private final ProcessingTaskService taskService;
  private final IndexingService indexingService;
  private final TaskPublisher publisher;

  public IndexTaskConsumer(
      ProcessingTaskService taskService, IndexingService indexingService, TaskPublisher publisher) {
    this.taskService = taskService;
    this.indexingService = indexingService;
    this.publisher = publisher;
  }

  @RabbitListener(queues = "#{indexWorkQueue.name}", ackMode = "MANUAL")
  public void onMessage(
      String taskIdMessage, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    long taskId = Long.parseLong(taskIdMessage);
    log.debug("Received index task message {}", taskId);
    ProcessingTask task = taskService.claim(taskId);
    if (task == null) {
      // 并发已被认领或重复投递：ack 忽略，避免重复处理。
      log.debug("Index task {} claim failed (already claimed or terminal), ack-ignoring", taskId);
      ackQuietly(channel, deliveryTag);
      return;
    }
    log.debug("Claimed index task {}", taskId);
    try {
      indexingService.execute(task);
      taskService.markSucceeded(taskId);
      ackQuietly(channel, deliveryTag);
    } catch (RetryableIndexException e) {
      handleRetryable(task, e, channel, deliveryTag);
    } catch (TerminalIndexException e) {
      handleTerminal(task, e, channel, deliveryTag);
    } catch (RuntimeException e) {
      // 未知错误按可重试处理（保守），避免未知故障直接丢弃任务。
      log.error("Unknown failure indexing task {}", taskId, e);
      handleRetryable(
          task,
          new RetryableIndexException(ErrorCode.INDEX_UNKNOWN_FAILURE, e.getMessage(), e),
          channel,
          deliveryTag);
    }
  }

  private void handleRetryable(
      ProcessingTask task, RetryableIndexException e, Channel channel, long deliveryTag) {
    int nextRetry = taskService.failRetryable(task.getId(), e.getFailureCode(), summary(e));
    if (nextRetry > 0) {
      // 进入 TTL 重试队列：level = retryCount - 1（0/1/2 对应 10s/1m/5m）
      publisher.publishToRetryQueue(task.getId(), nextRetry - 1);
      ackQuietly(channel, deliveryTag);
      log.warn(
          "Index task {} retryable failure code={}, scheduled retry {}",
          task.getId(),
          e.getFailureCode(),
          nextRetry);
    } else {
      // 重试耗尽 → FAILED，nack(requeue=false) 进 DLQ
      ackAndNackToDlq(channel, deliveryTag);
      log.warn("Index task {} exhausted retries, failureCode={}", task.getId(), e.getFailureCode());
    }
  }

  private void handleTerminal(
      ProcessingTask task, TerminalIndexException e, Channel channel, long deliveryTag) {
    taskService.failTerminal(task.getId(), e.getFailureCode(), summary(e));
    ackAndNackToDlq(channel, deliveryTag);
    log.warn("Index task {} terminal failure code={}", task.getId(), e.getFailureCode());
  }

  private void ackAndNackToDlq(Channel channel, long deliveryTag) {
    try {
      channel.basicNack(deliveryTag, false, false);
    } catch (Exception ex) {
      log.warn("Failed to nack message to DLQ", ex);
    }
  }

  private void ackQuietly(Channel channel, long deliveryTag) {
    try {
      channel.basicAck(deliveryTag, false);
    } catch (Exception ex) {
      log.warn("Failed to ack message", ex);
    }
  }

  private static String summary(Throwable e) {
    String msg = e.getMessage();
    return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
  }
}
