package knowflow.sanjin.modules.processing.service;

import knowflow.sanjin.common.config.RabbitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 任务发布器：事务提交后投递 RabbitMQ，失败仅告警不阻止业务提交。
 *
 * <p>消息为 {@code taskId} 小消息；Consumer 依据 taskId 从 MySQL 读取完整任务。发布失败时任务保持 PENDING 且
 * attemptedDeliveries 不递增，由恢复扫描重新发布，避免「数据库提交成功但消息永久丢失」。Consumer 进入 重试档位时也经本服务发布副本到对应 TTL 重试队列。
 */
@Service
public class TaskPublisher {

  private static final Logger log = LoggerFactory.getLogger(TaskPublisher.class);

  private final RabbitTemplate rabbitTemplate;
  private final RabbitProperties properties;

  public TaskPublisher(RabbitTemplate rabbitTemplate, RabbitProperties properties) {
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  /** 在当前事务提交后发布任务 id 到工作队列；无活动事务时立即发布。 */
  public void publishAfterCommit(long taskId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      publish(properties.workExchange(), properties.workQueueName(), taskId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publish(properties.workExchange(), properties.workQueueName(), taskId);
          }
        });
  }

  /** 发布任务 id 到第 level 档 TTL 重试队列（level 从 0 开始），用于可重试失败后的延迟重投。 */
  public void publishToRetryQueue(long taskId, int level) {
    publish(properties.retryExchange(), properties.retryQueueName(level), taskId);
  }

  private void publish(String exchange, String routingKey, long taskId) {
    try {
      rabbitTemplate.convertAndSend(exchange, routingKey, String.valueOf(taskId));
    } catch (RuntimeException e) {
      log.error("Failed to publish processing task {} to {}/{}", taskId, exchange, routingKey, e);
    }
  }
}
