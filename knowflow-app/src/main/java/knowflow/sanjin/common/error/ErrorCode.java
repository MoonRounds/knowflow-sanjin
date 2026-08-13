package knowflow.sanjin.common.error;

/** 稳定错误码目录：Problem Details 的 errorCode 统一从这里取值，作为前后端契约。 */
public final class ErrorCode {

  private ErrorCode() {}

  // ---- 通用 ----
  public static final String INVALID_ARGUMENT = "参数非法";
  public static final String VALIDATION_ERROR = "校验失败";
  public static final String INTERNAL_ERROR = "内部错误";
  public static final String IF_MATCH_REQUIRED = "缺少乐观锁版本";
  public static final String UNSUPPORTED_MEDIA_TYPE = "不支持的媒体类型";

  // ---- KnowledgeBase ----
  public static final String KNOWLEDGE_BASE_NOT_FOUND = "知识库不存在";
  public static final String KNOWLEDGE_BASE_NAME_CONFLICT = "知识库名称冲突";
  public static final String KNOWLEDGE_BASE_VERSION_CONFLICT = "知识库版本冲突";
  public static final String KNOWLEDGE_BASE_IN_USE = "知识库使用中";

  // ---- ModelConfig ----
  public static final String MODEL_CONFIG_NOT_FOUND = "模型配置不存在";
  public static final String MODEL_CONFIG_DISABLED = "模型配置已禁用";
  public static final String MODEL_CONFIG_IN_USE = "模型配置使用中";
  public static final String MODEL_CALL_TIMEOUT = "模型调用超时";
  public static final String UTILITY_CAPABILITY_TEST_REQUIRED = "Utility 能力测试未通过";
  public static final String MODEL_CONFIG_REVISION_CHANGED = "模型配置版本已变更";
  public static final String UTILITY_MODEL_NOT_CONFIGURED = "未配置Utility模型";
  public static final String MODEL_CONFIG_ROLE_CONFLICT = "默认聊天与Utility不能是同一模型";

  // ---- Conversation ----
  public static final String CONVERSATION_NOT_FOUND = "会话不存在";
  public static final String MESSAGE_NOT_FOUND = "消息不存在";
  public static final String ACTIVE_GENERATION_EXISTS = "存在进行中的生成";
  public static final String CONVERSATION_EXTRACTION_IN_PROGRESS = "会话正在提取知识中";
  public static final String NO_DEFAULT_MODEL_CONFIG = "未配置默认模型";
  public static final String GENERATION_CANCELLED = "生成已取消";

  // ---- Generation ----
  public static final String MODEL_CALL_FAILED = "模型调用失败";
  public static final String GENERATION_CLIENT_DISCONNECTED = "客户端已断开";

  // ---- KnowledgeItem ----
  public static final String KNOWLEDGE_ITEM_NOT_FOUND = "知识条目不存在";
  public static final String KNOWLEDGE_BASE_REF_NOT_FOUND = "引用的知识库不存在";
  public static final String KNOWLEDGE_ITEM_VERSION_CONFLICT = "知识条目版本冲突";
  public static final String KNOWLEDGE_INDEX_TASK_CONFLICT = "索引任务冲突";

  // ---- ProcessingTask ----
  public static final String PROCESSING_TASK_NOT_FOUND = "处理任务不存在";
  public static final String PROCESSING_TASK_RETRY_NOT_ALLOWED = "处理任务不允许重试";

  // ---- Conversation Title ----
  public static final String CONVERSATION_TITLE_GENERATION_FAILED = "会话标题生成失败";

  // ---- Knowledge Extraction ----
  public static final String EXTRACTION_INPUT_OVER_BUDGET = "提取输入超过预算";
  public static final String EXTRACTION_NO_COMPLETED_MESSAGES = "没有可提取的已完成消息";
  public static final String EXTRACTION_TASK_NOT_FOUND = "提取任务不存在";
  public static final String CANDIDATE_NOT_FOUND = "候选不存在";
  public static final String CANDIDATE_VERSION_CONFLICT = "候选版本冲突";
  public static final String CANDIDATE_INVALID_STATE = "候选状态非法";
  public static final String CANDIDATE_EMPTY_DRAFT = "候选草稿不完整";
  public static final String CANDIDATE_NO_KNOWLEDGE_BASE = "候选未关联知识库";

  // ---- Knowledge Index failure codes ----
  public static final String CHUNK_EMPTY = "分块为空";
  public static final String EMBEDDING_UNAVAILABLE = "向量模型不可用";
  public static final String EMBEDDING_AUTH_FAILURE = "向量模型认证失败";
  public static final String QDRANT_UNAVAILABLE = "Qdrant 不可用";
  public static final String INDEX_SCHEMA_FAILURE = "索引数据校验失败";
  public static final String INDEX_UNKNOWN_FAILURE = "索引未知错误";

  // ---- Document Upload ----
  public static final String FILE_INVALID_CONTENT = "文件内容非法";
  public static final String FILE_TOO_LARGE = "文件超过大小限制";
  public static final String FILE_UNSUPPORTED_TYPE = "不支持的文件类型";
  public static final String FILE_STORED_MISSING = "原文件缺失";
  public static final String DOCUMENT_PARSE_FAILED = "文档解析失败";
  public static final String DOCUMENT_PARSE_READ_FAILED = "文档读取失败";
  public static final String DOCUMENT_PARSE_UNKNOWN = "文档解析未知错误";
  public static final String DOCUMENT_FILE_NOT_FOUND = "文件元数据不存在";
}
