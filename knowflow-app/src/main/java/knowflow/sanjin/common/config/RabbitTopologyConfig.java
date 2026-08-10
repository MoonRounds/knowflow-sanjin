package knowflow.sanjin.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 可靠投递拓扑。
 *
 * <p>工作队列 → 固定 TTL 重试队列（10s/1m/5m）→ 重试队列到期死信回工作队列重新处理 → 最终失败进入 DLQ。 Consumer 采用「发布副本 + ack
 * 原消息」进入重试档位（按 DB retryCount 递增选择档位），最终失败才 nack（requeue=false）经工作队列 DLX 进入 DLQ。禁止 requeue=true
 * 无限即时重投。
 */
@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitTopologyConfig {

  private final RabbitProperties properties;

  public RabbitTopologyConfig(RabbitProperties properties) {
    this.properties = properties;
  }

  @Bean
  public DirectExchange workExchange() {
    return new DirectExchange(properties.workExchange());
  }

  @Bean
  public DirectExchange retryExchange() {
    return new DirectExchange(properties.retryExchange());
  }

  @Bean
  public DirectExchange dlxExchange() {
    return new DirectExchange(properties.dlxExchange());
  }

  /** 工作队列：最终失败经 DLX 进入 DLQ。 */
  @Bean
  public Queue indexWorkQueue() {
    return QueueBuilder.durable(properties.workQueueName())
        .deadLetterExchange(properties.dlxExchange())
        .deadLetterRoutingKey(properties.dlqName())
        .build();
  }

  /** 重试队列无 Consumer，TTL 到期后经工作交换机死信回 work 队列重新处理。 */
  @Bean
  public Queue indexRetryQueue0() {
    return retryQueue(0);
  }

  @Bean
  public Queue indexRetryQueue1() {
    return retryQueue(1);
  }

  @Bean
  public Queue indexRetryQueue2() {
    return retryQueue(2);
  }

  @Bean
  public Queue indexDlq() {
    return QueueBuilder.durable(properties.dlqName()).build();
  }

  @Bean
  public Binding bindIndexWorkQueue() {
    return BindingBuilder.bind(indexWorkQueue())
        .to(workExchange())
        .with(properties.workQueueName());
  }

  @Bean
  public Binding bindIndexRetryQueue0() {
    return bindRetryQueue(0);
  }

  @Bean
  public Binding bindIndexRetryQueue1() {
    return bindRetryQueue(1);
  }

  @Bean
  public Binding bindIndexRetryQueue2() {
    return bindRetryQueue(2);
  }

  @Bean
  public Binding bindIndexDlq() {
    return BindingBuilder.bind(indexDlq()).to(dlxExchange()).with(properties.dlqName());
  }

  private Queue retryQueue(int level) {
    return QueueBuilder.durable(properties.retryQueueName(level))
        .ttl((int) properties.getRetryDelays()[level].toMillis())
        .deadLetterExchange(properties.workExchange())
        .deadLetterRoutingKey(properties.workQueueName())
        .build();
  }

  private Binding bindRetryQueue(int level) {
    return BindingBuilder.bind(retryQueueBean(level))
        .to(retryExchange())
        .with(properties.retryQueueName(level));
  }

  private Queue retryQueueBean(int level) {
    return switch (level) {
      case 0 -> indexRetryQueue0();
      case 1 -> indexRetryQueue1();
      default -> indexRetryQueue2();
    };
  }
}
