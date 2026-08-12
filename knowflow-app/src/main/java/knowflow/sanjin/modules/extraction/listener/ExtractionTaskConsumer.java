package knowflow.sanjin.modules.extraction.listener;

import com.rabbitmq.client.Channel;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.extraction.exception.RetryableExtractionException;
import knowflow.sanjin.modules.extraction.exception.TerminalExtractionException;
import knowflow.sanjin.modules.extraction.service.ExtractionExecutor;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskService;
import knowflow.sanjin.modules.processing.service.TaskPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 知识提取 Consumer：幂等消费 taskId 消息，在提取工作队列上运行。
 *
 * <p>流程：claim（PENDING→PROCESSING，并发只胜一个）→ 读取快照与输入消息 → 执行结构化提取 → 落候选 → 成功 markSucceeded。 可重试失败 递增
 * retryCount 并发布副本到提取 TTL 重试队列、ack 原消息；重试耗尽或不可重试错误 markFailed 并 nack（requeue=false）进入提取 DLQ。 重复投递时
 * claim 失败直接 ack 忽略，保证不制造重复候选。
 */
@Component
public class ExtractionTaskConsumer {

  private static final Logger log = LoggerFactory.getLogger(ExtractionTaskConsumer.class);

  private final ProcessingTaskService taskService;
  private final ExtractionExecutor executor;
  private final TaskPublisher publisher;

  public ExtractionTaskConsumer(
      ProcessingTaskService taskService, ExtractionExecutor executor, TaskPublisher publisher) {
    this.taskService = taskService;
    this.executor = executor;
    this.publisher = publisher;
  }

  @RabbitListener(queues = "#{extractionWorkQueue.name}", ackMode = "MANUAL")
  public void onMessage(
      String taskIdMessage, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    long taskId = Long.parseLong(taskIdMessage);
    log.debug("收到提取任务消息 {}", taskId);
    ProcessingTask task = taskService.claim(taskId);
    if (task == null) {
      // 并发已被认领或重复投递：ack 忽略，避免重复处理。
      log.debug(
          "Extraction task {} claim failed (already claimed or terminal), ack-ignoring", taskId);
      ackQuietly(channel, deliveryTag);
      return;
    }
    log.debug("已认领提取任务 {}", taskId);
    try {
      executor.executeWithLookup(task);
      taskService.markSucceeded(taskId);
      ackQuietly(channel, deliveryTag);
    } catch (RetryableExtractionException e) {
      handleRetryable(task, e, channel, deliveryTag);
    } catch (TerminalExtractionException e) {
      handleTerminal(task, e, channel, deliveryTag);
    } catch (RuntimeException e) {
      // 未知错误按可重试处理（保守），避免未知故障直接丢弃任务。
      log.error("提取任务 {} 发生未知故障", taskId, e);
      handleRetryable(
          task,
          new RetryableExtractionException(ErrorCode.INDEX_UNKNOWN_FAILURE, e.getMessage(), e),
          channel,
          deliveryTag);
    }
  }

  private void handleRetryable(
      ProcessingTask task, RetryableExtractionException e, Channel channel, long deliveryTag) {
    int nextRetry = taskService.failWithDomainRetryable(task, e.getFailureCode(), summary(e));
    if (nextRetry > 0) {
      publisher.publishToExtractionRetryQueue(task.getId(), nextRetry - 1);
      ackQuietly(channel, deliveryTag);
      log.warn(
          "Extraction task {} retryable failure code={}, scheduled retry {}",
          task.getId(),
          e.getFailureCode(),
          nextRetry);
    } else {
      ackAndNackToDlq(channel, deliveryTag);
      log.warn(
          "Extraction task {} exhausted retries, failureCode={}", task.getId(), e.getFailureCode());
    }
  }

  private void handleTerminal(
      ProcessingTask task, TerminalExtractionException e, Channel channel, long deliveryTag) {
    taskService.failWithDomainTerminal(task, e.getFailureCode(), summary(e));
    ackAndNackToDlq(channel, deliveryTag);
    log.warn("提取任务 {} 终态失败 code={}", task.getId(), e.getFailureCode());
  }

  private void ackAndNackToDlq(Channel channel, long deliveryTag) {
    try {
      channel.basicNack(deliveryTag, false, false);
    } catch (Exception ex) {
      log.warn("提取消息 nack 到 DLQ 失败", ex);
    }
  }

  private void ackQuietly(Channel channel, long deliveryTag) {
    try {
      channel.basicAck(deliveryTag, false);
    } catch (Exception ex) {
      log.warn("提取消息 ack 失败", ex);
    }
  }

  private static String summary(Throwable e) {
    String msg = e.getMessage();
    return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
  }
}
