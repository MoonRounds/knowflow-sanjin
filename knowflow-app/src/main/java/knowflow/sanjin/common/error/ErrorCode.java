package knowflow.sanjin.common.error;

/** 稳定错误码目录：Problem Details 的 errorCode 统一从这里取值，作为前后端契约。 */
public final class ErrorCode {

  private ErrorCode() {}

  // ---- 通用 ----
  public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  public static final String IF_MATCH_REQUIRED = "IF_MATCH_REQUIRED";

  // ---- KnowledgeBase ----
  public static final String KNOWLEDGE_BASE_NOT_FOUND = "KNOWLEDGE_BASE_NOT_FOUND";
  public static final String KNOWLEDGE_BASE_NAME_CONFLICT = "KNOWLEDGE_BASE_NAME_CONFLICT";
  public static final String KNOWLEDGE_BASE_VERSION_CONFLICT = "KNOWLEDGE_BASE_VERSION_CONFLICT";
  public static final String KNOWLEDGE_BASE_IN_USE = "KNOWLEDGE_BASE_IN_USE";

  // ---- ModelConfig ----
  public static final String MODEL_CONFIG_NOT_FOUND = "MODEL_CONFIG_NOT_FOUND";
  public static final String MODEL_CONFIG_DISABLED = "MODEL_CONFIG_DISABLED";
  public static final String MODEL_CONFIG_IN_USE = "MODEL_CONFIG_IN_USE";
  public static final String MODEL_CALL_TIMEOUT = "MODEL_CALL_TIMEOUT";
  public static final String UTILITY_CAPABILITY_TEST_REQUIRED = "UTILITY_CAPABILITY_TEST_REQUIRED";
  public static final String MODEL_CONFIG_REVISION_CHANGED = "MODEL_CONFIG_REVISION_CHANGED";

  // ---- Conversation ----
  public static final String CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
  public static final String MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
  public static final String ACTIVE_GENERATION_EXISTS = "ACTIVE_GENERATION_EXISTS";
  public static final String NO_DEFAULT_MODEL_CONFIG = "NO_DEFAULT_MODEL_CONFIG";
  public static final String GENERATION_CANCELLED = "GENERATION_CANCELLED";

  // ---- Generation ----
  public static final String MODEL_CALL_FAILED = "MODEL_CALL_FAILED";
  public static final String GENERATION_CLIENT_DISCONNECTED = "GENERATION_CLIENT_DISCONNECTED";

  // ---- KnowledgeItem ----
  public static final String KNOWLEDGE_ITEM_NOT_FOUND = "KNOWLEDGE_ITEM_NOT_FOUND";
  public static final String KNOWLEDGE_BASE_REF_NOT_FOUND = "KNOWLEDGE_BASE_REF_NOT_FOUND";
  public static final String KNOWLEDGE_ITEM_VERSION_CONFLICT = "KNOWLEDGE_ITEM_VERSION_CONFLICT";
  public static final String KNOWLEDGE_INDEX_TASK_CONFLICT = "KNOWLEDGE_INDEX_TASK_CONFLICT";

  // ---- ProcessingTask ----
  public static final String PROCESSING_TASK_NOT_FOUND = "PROCESSING_TASK_NOT_FOUND";
  public static final String PROCESSING_TASK_RETRY_NOT_ALLOWED =
      "PROCESSING_TASK_RETRY_NOT_ALLOWED";

  // ---- Knowledge Index failure codes ----
  public static final String CHUNK_EMPTY = "CHUNK_EMPTY";
  public static final String EMBEDDING_UNAVAILABLE = "EMBEDDING_UNAVAILABLE";
  public static final String EMBEDDING_AUTH_FAILURE = "EMBEDDING_AUTH_FAILURE";
  public static final String QDRANT_UNAVAILABLE = "QDRANT_UNAVAILABLE";
  public static final String INDEX_SCHEMA_FAILURE = "INDEX_SCHEMA_FAILURE";
  public static final String INDEX_UNKNOWN_FAILURE = "INDEX_UNKNOWN_FAILURE";
}
