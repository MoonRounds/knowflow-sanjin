package knowflow.sanjin.modules.extraction;

/** 知识提取领域常量：任务类型、状态、profile 版本与 business key 结构。 */
public final class ExtractionConstants {

  private ExtractionConstants() {}

  public static final String TASK_TYPE_EXTRACTION = "EXTRACTION";

  public static final String CANDIDATE_PENDING = "PENDING";
  public static final String CANDIDATE_CONFIRMED = "CONFIRMED";
  public static final String CANDIDATE_REJECTED = "REJECTED";

  public static final String EXTRACTION_PROFILE = "DEFAULT";
  public static final int EXTRACTION_PROFILE_VERSION = 1;

  /**
   * 提取任务 business
   * key：EXTRACTION:{conversationId}:{cutoffMessageId}:{utilityRevisionId}:{profileVersion}
   */
  public static final String BUSINESS_KEY_PREFIX = "EXTRACTION:";

  public static final String BUSINESS_KEY_DELIMITER = ":";

  /**
   * 提取任务 work 队列基名（须与 RabbitProperties.extractionWorkQueue 一致）：TaskPublisher 经 workQueueName(base)
   * 派生 routingKey，若只给 "extraction" 会路由不到 "…extraction.work" 队列。
   */
  public static final String WORK_QUEUE_BASE = "extraction.work";

  /** 确认后创建 Item 的来源类型。 */
  public static final String SOURCE_AI_CONVERSATION = "AI_CONVERSATION";
}
