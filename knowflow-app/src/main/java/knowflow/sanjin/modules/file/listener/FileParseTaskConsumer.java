package knowflow.sanjin.modules.file.listener;

import com.rabbitmq.client.Channel;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.file.exception.RetryableFileException;
import knowflow.sanjin.modules.file.exception.TerminalFileException;
import knowflow.sanjin.modules.file.service.FileParsingService;
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
 * 文档解析 Consumer：幂等消费 taskId 消息，在 document 工作队列上运行。
 *
 * <p>流程：claim → 解析（读取原文件、更新 Item、标记文件解析成功）→ 提交索引任务 → markSucceeded。可重试失败递增 retryCount 并发布副本到文档 TTL
 * 重试队列、ack 原消息；重试耗尽或不可重试错误 markFailed 并 nack（requeue=false）进入文档 DLQ。 重复投递时 claim 失败直接 ack 忽略，不重复解析。
 */
@Component
public class FileParseTaskConsumer {

  private static final Logger log = LoggerFactory.getLogger(FileParseTaskConsumer.class);

  private final ProcessingTaskService taskService;
  private final FileParsingService parsingService;
  private final TaskPublisher publisher;

  public FileParseTaskConsumer(
      ProcessingTaskService taskService,
      FileParsingService parsingService,
      TaskPublisher publisher) {
    this.taskService = taskService;
    this.parsingService = parsingService;
    this.publisher = publisher;
  }

  @RabbitListener(queues = "#{documentWorkQueue.name}", ackMode = "MANUAL")
  public void onMessage(
      String taskIdMessage, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    long taskId = Long.parseLong(taskIdMessage);
    log.debug("收到文档解析任务消息 {}", taskId);
    ProcessingTask task = taskService.claim(taskId);
    if (task == null) {
      log.debug("文档解析任务 {} 认领失败（已被认领或已终态），ack 忽略", taskId);
      ackQuietly(channel, deliveryTag);
      return;
    }
    log.debug("已认领文档解析任务 {}", taskId);
    try {
      parsingService.execute(task);
      taskService.markSucceeded(taskId);
      ackQuietly(channel, deliveryTag);
    } catch (RetryableFileException e) {
      handleRetryable(task, e, channel, deliveryTag);
    } catch (TerminalFileException e) {
      handleTerminal(task, e, channel, deliveryTag);
    } catch (RuntimeException e) {
      log.error("文档解析任务 {} 发生未知故障", taskId, e);
      handleRetryable(
          task,
          new RetryableFileException(ErrorCode.DOCUMENT_PARSE_UNKNOWN + ": " + e.getMessage(), e),
          channel,
          deliveryTag);
    }
  }

  private void handleRetryable(
      ProcessingTask task, RetryableFileException e, Channel channel, long deliveryTag) {
    int nextRetry = taskService.failWithDomainRetryable(task, e.getFailureCode(), summary(e));
    if (nextRetry > 0) {
      publisher.publishToDocumentRetryQueue(task.getId(), nextRetry - 1);
      ackQuietly(channel, deliveryTag);
      log.warn("文档解析任务 {} 可重试失败，已安排重试 {}", task.getId(), nextRetry);
    } else {
      ackAndNackToDlq(channel, deliveryTag);
      log.warn("文档解析任务 {} 重试耗尽，failureCode={}", task.getId(), e.getFailureCode());
    }
  }

  private void handleTerminal(
      ProcessingTask task, TerminalFileException e, Channel channel, long deliveryTag) {
    taskService.failWithDomainTerminal(task, e.getFailureCode(), summary(e));
    ackAndNackToDlq(channel, deliveryTag);
    log.warn("文档解析任务 {} 终态失败 code={}", task.getId(), e.getFailureCode());
  }

  private void ackAndNackToDlq(Channel channel, long deliveryTag) {
    try {
      channel.basicNack(deliveryTag, false, false);
    } catch (Exception ex) {
      log.warn("文档解析消息 nack 到 DLQ 失败", ex);
    }
  }

  private void ackQuietly(Channel channel, long deliveryTag) {
    try {
      channel.basicAck(deliveryTag, false);
    } catch (Exception ex) {
      log.warn("文档解析消息 ack 失败", ex);
    }
  }

  private static String summary(Throwable e) {
    String msg = e.getMessage();
    return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
  }
}
