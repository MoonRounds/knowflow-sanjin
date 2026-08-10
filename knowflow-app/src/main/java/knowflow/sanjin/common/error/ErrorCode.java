package knowflow.sanjin.common.error;

/** 稳定错误码目录：Problem Details 的 errorCode 统一从这里取值，作为前后端契约。 */
public final class ErrorCode {

  private ErrorCode() {}

  // ---- 通用 ----
  public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  // ---- KnowledgeBase ----
  public static final String KNOWLEDGE_BASE_NOT_FOUND = "KNOWLEDGE_BASE_NOT_FOUND";
  public static final String KNOWLEDGE_BASE_NAME_CONFLICT = "KNOWLEDGE_BASE_NAME_CONFLICT";
  public static final String KNOWLEDGE_BASE_VERSION_CONFLICT = "KNOWLEDGE_BASE_VERSION_CONFLICT";

  // ---- ModelConfig ----
  public static final String MODEL_CONFIG_NOT_FOUND = "MODEL_CONFIG_NOT_FOUND";
  public static final String MODEL_CONFIG_DISABLED = "MODEL_CONFIG_DISABLED";
  public static final String MODEL_CONFIG_IN_USE = "MODEL_CONFIG_IN_USE";
  public static final String MODEL_CALL_TIMEOUT = "MODEL_CALL_TIMEOUT";

  // ---- Conversation ----
  public static final String CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
  public static final String MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
  public static final String ACTIVE_GENERATION_EXISTS = "ACTIVE_GENERATION_EXISTS";
  public static final String NO_DEFAULT_MODEL_CONFIG = "NO_DEFAULT_MODEL_CONFIG";
  public static final String GENERATION_CANCELLED = "GENERATION_CANCELLED";

  // ---- Generation ----
  public static final String MODEL_CALL_FAILED = "MODEL_CALL_FAILED";
  public static final String GENERATION_CLIENT_DISCONNECTED = "GENERATION_CLIENT_DISCONNECTED";
}
