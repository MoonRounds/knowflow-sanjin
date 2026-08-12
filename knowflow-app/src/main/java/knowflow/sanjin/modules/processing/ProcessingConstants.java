package knowflow.sanjin.modules.processing;

/** 异步任务常量：任务类型与状态。 */
public final class ProcessingConstants {

  private ProcessingConstants() {}

  public static final String TASK_TYPE_KNOWLEDGE_INDEX = "KNOWLEDGE_INDEX";
  public static final String TASK_TYPE_KNOWLEDGE_DELETE = "KNOWLEDGE_DELETE";
  public static final String TASK_TYPE_CONVERSATION_TITLE = "CONVERSATION_TITLE";

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PROCESSING = "PROCESSING";
  public static final String STATUS_SUCCEEDED = "SUCCEEDED";
  public static final String STATUS_FAILED = "FAILED";
}
