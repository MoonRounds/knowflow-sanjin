package knowflow.sanjin.modules.document;

/** 文档上传模块常量：任务类型、状态、队列基名与错误码前缀。 */
public final class DocumentConstants {

  private DocumentConstants() {}

  public static final String TASK_TYPE_DOCUMENT_PARSE = "DOCUMENT_PARSE";

  /**
   * document 任务 work 队列基名（须与 RabbitProperties.documentWorkQueue 一致）：TaskPublisher 经
   * workQueueName(base) 拼接。
   */
  public static final String WORK_QUEUE_BASE = "document.work";

  public static final String FILE_STATUS_ACTIVE = "ACTIVE";
  public static final String FILE_STATUS_DELETED = "DELETED";

  public static final String PARSE_STATUS_PENDING = "PENDING";
  public static final String PARSE_STATUS_PROCESSING = "PROCESSING";
  public static final String PARSE_STATUS_SUCCEEDED = "SUCCEEDED";
  public static final String PARSE_STATUS_FAILED = "FAILED";

  /** 文档解析任务 business key 结构：DOCUMENT:{fileMetadataId}。 */
  public static final String BUSINESS_KEY_PREFIX = "DOCUMENT:";
}
