package knowflow.sanjin.modules.conversation.title;

import com.rabbitmq.client.Channel;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.conversation.exception.RetryableTitleException;
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
 * 会话标题 Consumer：幂等消费 taskId 消息，在会话标题工作队列上运行。
 *
 * <p>流程：claim → {@link ConversationTitleService#execute}（内部已做失败回退，正常不抛业务异常）→ 成功 markSucceeded +
 * ack。可重试失败递增 retryCount 并发布副本到标题 TTL 重试队列、ack 原消息；重试耗尽或 不可重试错误 markFailed 并
 * nack（requeue=false）进入标题 DLQ。重复投递时 claim 失败直接 ack 忽略。
 */
@Component
public class ConversationTitleTaskConsumer {

  private static final Logger log = LoggerFactory.getLogger(ConversationTitleTaskConsumer.class);

  private final ProcessingTaskService taskService;
  private final ConversationTitleService titleService;
  private final TaskPublisher publisher;

  public ConversationTitleTaskConsumer(
      ProcessingTaskService taskService,
      ConversationTitleService titleService,
      TaskPublisher publisher) {
    this.taskService = taskService;
    this.titleService = titleService;
    this.publisher = publisher;
  }

  @RabbitListener(queues = "#{conversationTitleWorkQueue.name}", ackMode = "MANUAL")
  public void onMessage(
      String taskIdMessage, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    long taskId = Long.parseLong(taskIdMessage);
    log.debug("收到会话标题任务消息 {}", taskId);
    ProcessingTask task = taskService.claim(taskId);
    if (task == null) {
      // 并发已被认领或重复投递：ack 忽略，避免重复处理。
      log.debug("会话标题任务 {} 认领失败（已被认领或已终态），ack 忽略", taskId);
      ackQuietly(channel, deliveryTag);
      return;
    }
    log.debug("已认领会话标题任务 {}", taskId);
    try {
      titleService.execute(task);
      taskService.markSucceeded(taskId);
      ackQuietly(channel, deliveryTag);
    } catch (RetryableTitleException e) {
      handleRetryable(task, e, channel, deliveryTag);
    } catch (RuntimeException e) {
      // 未知错误按可重试处理（保守），避免未知故障直接丢弃任务。
      log.error("会话标题任务 {} 发生未知故障", taskId, e);
      handleRetryable(
          task,
          new RetryableTitleException(
              ErrorCode.CONVERSATION_TITLE_GENERATION_FAILED, e.getMessage(), e),
          channel,
          deliveryTag);
    }
  }

  private void handleRetryable(
      ProcessingTask task, RetryableTitleException e, Channel channel, long deliveryTag) {
    int nextRetry = taskService.failWithDomainRetryable(task, e.getFailureCode(), summary(e));
    if (nextRetry > 0) {
      // 进入 TTL 重试队列：level = retryCount - 1（0/1/2 对应 10s/1m/5m）
      publisher.publishToConversationTitleRetryQueue(task.getId(), nextRetry - 1);
      ackQuietly(channel, deliveryTag);
      log.warn("会话标题任务 {} 可重试失败 code={}，已安排重试 {}", task.getId(), e.getFailureCode(), nextRetry);
    } else {
      // 重试耗尽 → FAILED，nack(requeue=false) 进 DLQ
      ackAndNackToDlq(channel, deliveryTag);
      log.warn("会话标题任务 {} 重试耗尽，failureCode={}", task.getId(), e.getFailureCode());
    }
  }

  private void ackAndNackToDlq(Channel channel, long deliveryTag) {
    try {
      channel.basicNack(deliveryTag, false, false);
    } catch (Exception ex) {
      log.warn("消息 nack 到 DLQ 失败", ex);
    }
  }

  private void ackQuietly(Channel channel, long deliveryTag) {
    try {
      channel.basicAck(deliveryTag, false);
    } catch (Exception ex) {
      log.warn("消息 ack 失败", ex);
    }
  }

  private static String summary(Throwable e) {
    String msg = e.getMessage();
    return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
  }
}
