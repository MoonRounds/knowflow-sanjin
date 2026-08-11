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

  /** 提取工作队列：与索引队列复用同一交换机，独立 routingKey 与重试/DLQ 链。 */
  @Bean
  public Queue extractionWorkQueue() {
    return QueueBuilder.durable(properties.extractionWorkQueueName())
        .deadLetterExchange(properties.dlxExchange())
        .deadLetterRoutingKey(properties.extractionDlqName())
        .build();
  }

  /** 文档解析工作队列：独立 routingKey 与重试/DLQ 链。 */
  @Bean
  public Queue documentWorkQueue() {
    return QueueBuilder.durable(properties.documentWorkQueueName())
        .deadLetterExchange(properties.dlxExchange())
        .deadLetterRoutingKey(properties.documentDlqName())
        .build();
  }

  /** 重试队列无 Consumer，TTL 到期后经工作交换机死信回 work 队列重新处理。 */
  @Bean
  public Queue indexRetryQueue0() {
    return retryQueue(0, properties.retryQueueName(0), properties.workQueueName());
  }

  @Bean
  public Queue indexRetryQueue1() {
    return retryQueue(1, properties.retryQueueName(1), properties.workQueueName());
  }

  @Bean
  public Queue indexRetryQueue2() {
    return retryQueue(2, properties.retryQueueName(2), properties.workQueueName());
  }

  /** 提取重试队列：TTL 到期死信回提取工作队列。 */
  @Bean
  public Queue extractionRetryQueue0() {
    return retryQueue(
        0, properties.extractionRetryQueueName(0), properties.extractionWorkQueueName());
  }

  @Bean
  public Queue extractionRetryQueue1() {
    return retryQueue(
        1, properties.extractionRetryQueueName(1), properties.extractionWorkQueueName());
  }

  @Bean
  public Queue extractionRetryQueue2() {
    return retryQueue(
        2, properties.extractionRetryQueueName(2), properties.extractionWorkQueueName());
  }

  /** 文档解析重试队列：TTL 到期死信回文档解析工作队列。 */
  @Bean
  public Queue documentRetryQueue0() {
    return retryQueue(0, properties.documentRetryQueueName(0), properties.documentWorkQueueName());
  }

  @Bean
  public Queue documentRetryQueue1() {
    return retryQueue(1, properties.documentRetryQueueName(1), properties.documentWorkQueueName());
  }

  @Bean
  public Queue documentRetryQueue2() {
    return retryQueue(2, properties.documentRetryQueueName(2), properties.documentWorkQueueName());
  }

  @Bean
  public Queue indexDlq() {
    return QueueBuilder.durable(properties.dlqName()).build();
  }

  @Bean
  public Queue extractionDlq() {
    return QueueBuilder.durable(properties.extractionDlqName()).build();
  }

  @Bean
  public Queue documentDlq() {
    return QueueBuilder.durable(properties.documentDlqName()).build();
  }

  @Bean
  public Binding bindIndexWorkQueue() {
    return BindingBuilder.bind(indexWorkQueue())
        .to(workExchange())
        .with(properties.workQueueName());
  }

  @Bean
  public Binding bindExtractionWorkQueue() {
    return BindingBuilder.bind(extractionWorkQueue())
        .to(workExchange())
        .with(properties.extractionWorkQueueName());
  }

  @Bean
  public Binding bindDocumentWorkQueue() {
    return BindingBuilder.bind(documentWorkQueue())
        .to(workExchange())
        .with(properties.documentWorkQueueName());
  }

  @Bean
  public Binding bindIndexRetryQueue0() {
    return bindRetryQueue(0, indexRetryQueue0(), properties.retryQueueName(0));
  }

  @Bean
  public Binding bindIndexRetryQueue1() {
    return bindRetryQueue(1, indexRetryQueue1(), properties.retryQueueName(1));
  }

  @Bean
  public Binding bindIndexRetryQueue2() {
    return bindRetryQueue(2, indexRetryQueue2(), properties.retryQueueName(2));
  }

  @Bean
  public Binding bindExtractionRetryQueue0() {
    return bindRetryQueue(0, extractionRetryQueue0(), properties.extractionRetryQueueName(0));
  }

  @Bean
  public Binding bindExtractionRetryQueue1() {
    return bindRetryQueue(1, extractionRetryQueue1(), properties.extractionRetryQueueName(1));
  }

  @Bean
  public Binding bindExtractionRetryQueue2() {
    return bindRetryQueue(2, extractionRetryQueue2(), properties.extractionRetryQueueName(2));
  }

  @Bean
  public Binding bindDocumentRetryQueue0() {
    return bindRetryQueue(0, documentRetryQueue0(), properties.documentRetryQueueName(0));
  }

  @Bean
  public Binding bindDocumentRetryQueue1() {
    return bindRetryQueue(1, documentRetryQueue1(), properties.documentRetryQueueName(1));
  }

  @Bean
  public Binding bindDocumentRetryQueue2() {
    return bindRetryQueue(2, documentRetryQueue2(), properties.documentRetryQueueName(2));
  }

  @Bean
  public Binding bindIndexDlq() {
    return BindingBuilder.bind(indexDlq()).to(dlxExchange()).with(properties.dlqName());
  }

  @Bean
  public Binding bindExtractionDlq() {
    return BindingBuilder.bind(extractionDlq())
        .to(dlxExchange())
        .with(properties.extractionDlqName());
  }

  @Bean
  public Binding bindDocumentDlq() {
    return BindingBuilder.bind(documentDlq()).to(dlxExchange()).with(properties.documentDlqName());
  }

  /** 构建 TTL 重试队列：TTL 到期后经 workExchange 死信回 workQueue 名称对应的队列。 */
  private Queue retryQueue(int level, String retryQueueName, String dlRoutingKey) {
    return QueueBuilder.durable(retryQueueName)
        .ttl((int) properties.getRetryDelays()[level].toMillis())
        .deadLetterExchange(properties.workExchange())
        .deadLetterRoutingKey(dlRoutingKey)
        .build();
  }

  private Binding bindRetryQueue(int level, Queue queue, String routingKey) {
    return BindingBuilder.bind(queue).to(retryExchange()).with(routingKey);
  }
}
