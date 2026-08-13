package knowflow.sanjin.common.exception;

import java.util.UUID;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.common.filter.CorrelationIdFilter;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationExtractionInProgressException;
import knowflow.sanjin.modules.conversation.exception.ConversationKnowledgeBaseDisabledException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.exception.ConversationVersionConflictException;
import knowflow.sanjin.modules.conversation.exception.MessageNotFoundException;
import knowflow.sanjin.modules.conversation.exception.NoDefaultModelConfigException;
import knowflow.sanjin.modules.embeddingconfig.exception.EmbeddingConfigDimensionChangeException;
import knowflow.sanjin.modules.extraction.exception.CandidateEmptyDraftException;
import knowflow.sanjin.modules.extraction.exception.CandidateInvalidStateException;
import knowflow.sanjin.modules.extraction.exception.CandidateNoKnowledgeBaseException;
import knowflow.sanjin.modules.extraction.exception.CandidateNotFoundException;
import knowflow.sanjin.modules.extraction.exception.CandidateVersionConflictException;
import knowflow.sanjin.modules.extraction.exception.ExtractionInputOverBudgetException;
import knowflow.sanjin.modules.extraction.exception.ExtractionNoCompletedMessagesException;
import knowflow.sanjin.modules.extraction.exception.ExtractionTaskNotFoundException;
import knowflow.sanjin.modules.file.exception.FileMetadataNotFoundException;
import knowflow.sanjin.modules.file.exception.FileTooLargeException;
import knowflow.sanjin.modules.file.exception.FileUnsupportedTypeException;
import knowflow.sanjin.modules.file.exception.InvalidFileContentException;
import knowflow.sanjin.modules.file.exception.StoredFileMissingException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeBaseRefNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentVersionConflictException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeIndexTaskConflictException;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseInUseException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNameConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.modelconfig.exception.ModelCallTimeoutException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigDisabledException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigInUseException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigRevisionChangedException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigRoleConflictException;
import knowflow.sanjin.modules.modelconfig.exception.UtilityCapabilityRequiredException;
import knowflow.sanjin.modules.modelconfig.exception.UtilityModelNotConfiguredException;
import knowflow.sanjin.modules.processing.exception.ProcessingTaskNotFoundException;
import knowflow.sanjin.modules.processing.exception.ProcessingTaskRetryNotAllowedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常 → RFC 9457 Problem Details 转换：稳定 errorCode + correlationId。
 *
 * <p>业务异常映射为稳定错误码；通用异常（校验失败、非法参数）归类为 400；未知异常记日志并返回 通用 500，不透传内部细节。correlationId 优先取自 {@link
 * CorrelationIdFilter} 写入的 MDC，使响应与同一请求的日志关联；无 MDC 时兜底生成。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(KnowledgeBaseNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(KnowledgeBaseNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "知识库不存在",
        "知识库 id=" + ex.getId() + " 不存在或不可访问。",
        ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
  }

  @ExceptionHandler(KnowledgeBaseNameConflictException.class)
  public ResponseEntity<ProblemDetail> handleNameConflict(KnowledgeBaseNameConflictException ex) {
    return problem(
        HttpStatus.CONFLICT, "名称冲突", "已存在同名规范化知识库。", ErrorCode.KNOWLEDGE_BASE_NAME_CONFLICT);
  }

  @ExceptionHandler(KnowledgeBaseVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleVersionConflict(
      KnowledgeBaseVersionConflictException ex) {
    return problem(
        HttpStatus.CONFLICT, "版本冲突", ex.getMessage(), ErrorCode.KNOWLEDGE_BASE_VERSION_CONFLICT);
  }

  @ExceptionHandler(KnowledgeBaseInUseException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeBaseInUse(KnowledgeBaseInUseException ex) {
    return problem(HttpStatus.CONFLICT, "知识库使用中", ex.getMessage(), ErrorCode.KNOWLEDGE_BASE_IN_USE);
  }

  @ExceptionHandler(KnowledgeDocumentNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeDocumentNotFound(
      KnowledgeDocumentNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "知识条目不存在",
        "知识条目 id=" + ex.getId() + " 不存在或不可访问。",
        ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND);
  }

  @ExceptionHandler(KnowledgeBaseRefNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeBaseRefNotFound(
      KnowledgeBaseRefNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "知识库不存在",
        "引用的知识库 id=" + ex.getKnowledgeBaseId() + " 不存在或不可访问。",
        ErrorCode.KNOWLEDGE_BASE_REF_NOT_FOUND);
  }

  @ExceptionHandler(KnowledgeDocumentVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeDocumentVersionConflict(
      KnowledgeDocumentVersionConflictException ex) {
    return problem(
        HttpStatus.CONFLICT, "版本冲突", ex.getMessage(), ErrorCode.KNOWLEDGE_ITEM_VERSION_CONFLICT);
  }

  @ExceptionHandler(KnowledgeIndexTaskConflictException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeIndexTaskConflict(
      KnowledgeIndexTaskConflictException ex) {
    return problem(
        HttpStatus.CONFLICT, "索引任务冲突", ex.getMessage(), ErrorCode.KNOWLEDGE_INDEX_TASK_CONFLICT);
  }

  @ExceptionHandler(RetryableIndexException.class)
  public ResponseEntity<ProblemDetail> handleRetryableIndex(RetryableIndexException ex) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "索引依赖不可用", ex.getMessage(), ex.getFailureCode());
  }

  @ExceptionHandler(TerminalIndexException.class)
  public ResponseEntity<ProblemDetail> handleTerminalIndex(TerminalIndexException ex) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "索引数据校验失败", ex.getMessage(), ex.getFailureCode());
  }

  @ExceptionHandler(EmbeddingConfigDimensionChangeException.class)
  public ResponseEntity<ProblemDetail> handleEmbeddingDimensionChange(
      EmbeddingConfigDimensionChangeException ex) {
    return problem(HttpStatus.CONFLICT, "需要先重建索引", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(ProcessingTaskNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleProcessingTaskNotFound(
      ProcessingTaskNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "处理任务不存在",
        "处理任务 id=" + ex.getId() + " 不存在或不可访问。",
        ErrorCode.PROCESSING_TASK_NOT_FOUND);
  }

  @ExceptionHandler(ProcessingTaskRetryNotAllowedException.class)
  public ResponseEntity<ProblemDetail> handleProcessingTaskRetryNotAllowed(
      ProcessingTaskRetryNotAllowedException ex) {
    return problem(
        HttpStatus.CONFLICT, "不允许重试", ex.getMessage(), ErrorCode.PROCESSING_TASK_RETRY_NOT_ALLOWED);
  }

  @ExceptionHandler(ModelConfigNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigNotFound(ModelConfigNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "模型配置不存在",
        "模型配置 id=" + ex.getId() + " 不存在或不可访问。",
        ErrorCode.MODEL_CONFIG_NOT_FOUND);
  }

  @ExceptionHandler(ModelConfigDisabledException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigDisabled(ModelConfigDisabledException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "模型配置已禁用",
        "模型配置 id=" + ex.getId() + " 已禁用，无法使用。",
        ErrorCode.MODEL_CONFIG_DISABLED);
  }

  @ExceptionHandler(ModelConfigInUseException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigInUse(ModelConfigInUseException ex) {
    return problem(HttpStatus.CONFLICT, "模型配置使用中", ex.getMessage(), ErrorCode.MODEL_CONFIG_IN_USE);
  }

  @ExceptionHandler(UtilityCapabilityRequiredException.class)
  public ResponseEntity<ProblemDetail> handleUtilityCapabilityRequired(
      UtilityCapabilityRequiredException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "需要先通过能力测试",
        ex.getMessage(),
        ErrorCode.UTILITY_CAPABILITY_TEST_REQUIRED);
  }

  @ExceptionHandler(ModelConfigRoleConflictException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigRoleConflict(
      ModelConfigRoleConflictException ex) {
    return problem(
        HttpStatus.CONFLICT, "模型角色冲突", ex.getMessage(), ErrorCode.MODEL_CONFIG_ROLE_CONFLICT);
  }

  @ExceptionHandler(UtilityModelNotConfiguredException.class)
  public ResponseEntity<ProblemDetail> handleUtilityModelNotConfigured(
      UtilityModelNotConfiguredException ex) {
    return problem(HttpStatus.BAD_REQUEST, "未配置Utility模型", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(ModelConfigRevisionChangedException.class)
  public ResponseEntity<ProblemDetail> handleRevisionChanged(
      ModelConfigRevisionChangedException ex) {
    return problem(
        HttpStatus.CONFLICT, "模型配置版本已变更", ex.getMessage(), ErrorCode.MODEL_CONFIG_REVISION_CHANGED);
  }

  @ExceptionHandler(ModelCallTimeoutException.class)
  public ResponseEntity<ProblemDetail> handleModelCallTimeout(ModelCallTimeoutException ex) {
    return problem(
        HttpStatus.GATEWAY_TIMEOUT, "模型调用超时", "模型调用未在允许时间内完成。", ErrorCode.MODEL_CALL_TIMEOUT);
  }

  @ExceptionHandler(ConversationNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleConversationNotFound(
      ConversationNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "会话不存在",
        "会话 id=" + ex.getId() + " 不存在或不可访问。",
        ErrorCode.CONVERSATION_NOT_FOUND);
  }

  @ExceptionHandler(ConversationVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleConversationVersionConflict(
      ConversationVersionConflictException ex) {
    return problem(
        HttpStatus.CONFLICT, "会话版本冲突", ex.getMessage(), ErrorCode.CONVERSATION_VERSION_CONFLICT);
  }

  @ExceptionHandler(ConversationKnowledgeBaseDisabledException.class)
  public ResponseEntity<ProblemDetail> handleConversationKnowledgeBaseDisabled(
      ConversationKnowledgeBaseDisabledException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "知识库已禁用",
        ex.getMessage(),
        ErrorCode.CONVERSATION_KNOWLEDGE_BASE_DISABLED);
  }

  @ExceptionHandler(MessageNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleMessageNotFound(MessageNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "消息不存在",
        "会话 " + ex.getConversationId() + " 中的消息 " + ex.getMessageId() + " 不存在。",
        ErrorCode.MESSAGE_NOT_FOUND);
  }

  @ExceptionHandler(ActiveGenerationExistsException.class)
  public ResponseEntity<ProblemDetail> handleActiveGenerationExists(
      ActiveGenerationExistsException ex) {
    return problem(
        HttpStatus.CONFLICT, "存在进行中的生成", ex.getMessage(), ErrorCode.ACTIVE_GENERATION_EXISTS);
  }

  @ExceptionHandler(ConversationExtractionInProgressException.class)
  public ResponseEntity<ProblemDetail> handleConversationExtractionInProgress(
      ConversationExtractionInProgressException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "会话正在提取知识中",
        ex.getMessage(),
        ErrorCode.CONVERSATION_EXTRACTION_IN_PROGRESS);
  }

  @ExceptionHandler(NoDefaultModelConfigException.class)
  public ResponseEntity<ProblemDetail> handleNoDefaultModelConfig(
      NoDefaultModelConfigException ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "未配置默认模型",
        "未配置默认聊天模型，请先选择模型再发送消息。",
        ErrorCode.NO_DEFAULT_MODEL_CONFIG);
  }

  @ExceptionHandler(ExtractionInputOverBudgetException.class)
  public ResponseEntity<ProblemDetail> handleExtractionInputOverBudget(
      ExtractionInputOverBudgetException ex) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "提取输入超过预算", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(ExtractionNoCompletedMessagesException.class)
  public ResponseEntity<ProblemDetail> handleExtractionNoCompletedMessages(
      ExtractionNoCompletedMessagesException ex) {
    return problem(HttpStatus.BAD_REQUEST, "没有可提取的已完成消息", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(ExtractionTaskNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleExtractionTaskNotFound(
      ExtractionTaskNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND, "提取任务不存在", "提取任务 id=" + ex.getId() + " 不存在或不可访问。", ex.getErrorCode());
  }

  @ExceptionHandler(CandidateNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleCandidateNotFound(CandidateNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND, "候选不存在", "候选 id=" + ex.getId() + " 不存在或不可访问。", ex.getErrorCode());
  }

  @ExceptionHandler(CandidateVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleCandidateVersionConflict(
      CandidateVersionConflictException ex) {
    return problem(HttpStatus.CONFLICT, "候选版本冲突", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(CandidateInvalidStateException.class)
  public ResponseEntity<ProblemDetail> handleCandidateInvalidState(
      CandidateInvalidStateException ex) {
    return problem(HttpStatus.CONFLICT, "候选状态非法", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(CandidateEmptyDraftException.class)
  public ResponseEntity<ProblemDetail> handleCandidateEmptyDraft(CandidateEmptyDraftException ex) {
    return problem(HttpStatus.BAD_REQUEST, "候选草稿不完整", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(CandidateNoKnowledgeBaseException.class)
  public ResponseEntity<ProblemDetail> handleCandidateNoKnowledgeBase(
      CandidateNoKnowledgeBaseException ex) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "候选未关联知识库", ex.getMessage(), ex.getErrorCode());
  }

  @ExceptionHandler(PreconditionRequiredException.class)
  public ResponseEntity<ProblemDetail> handlePreconditionRequired(
      PreconditionRequiredException ex) {
    return problem(
        HttpStatus.PRECONDITION_REQUIRED, "缺少乐观锁版本", ex.getMessage(), ErrorCode.IF_MATCH_REQUIRED);
  }

  @ExceptionHandler(FileTooLargeException.class)
  public ResponseEntity<ProblemDetail> handleFileTooLarge(FileTooLargeException ex) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "文件超过大小限制", ex.getMessage(), ErrorCode.FILE_TOO_LARGE);
  }

  /** multipart 解析层超限：统一映射为 422 + FILE_TOO_LARGE（与业务层 FileTooLargeException 一致）。 */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "文件超过大小限制", "上传文件超过大小上限", ErrorCode.FILE_TOO_LARGE);
  }

  @ExceptionHandler(FileUnsupportedTypeException.class)
  public ResponseEntity<ProblemDetail> handleFileUnsupportedType(FileUnsupportedTypeException ex) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "不支持的文件类型",
        ex.getMessage(),
        ErrorCode.FILE_UNSUPPORTED_TYPE);
  }

  @ExceptionHandler(InvalidFileContentException.class)
  public ResponseEntity<ProblemDetail> handleInvalidFileContent(InvalidFileContentException ex) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, "文件内容非法", ex.getMessage(), ErrorCode.FILE_INVALID_CONTENT);
  }

  @ExceptionHandler(StoredFileMissingException.class)
  public ResponseEntity<ProblemDetail> handleStoredFileMissing(StoredFileMissingException ex) {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "原文件缺失", ex.getMessage(), ErrorCode.FILE_STORED_MISSING);
  }

  @ExceptionHandler(FileMetadataNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleFileMetadataNotFound(
      FileMetadataNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "文件元数据不存在",
        "文件元数据 id=" + ex.getId() + " 不存在或不可访问。",
        ErrorCode.DOCUMENT_FILE_NOT_FOUND);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, "参数非法", ex.getMessage(), ErrorCode.INVALID_ARGUMENT);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElse("参数校验失败");
    return problem(HttpStatus.BAD_REQUEST, "校验失败", detail, ErrorCode.VALIDATION_ERROR);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex) {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "不支持的媒体类型",
        "请求的 Content-Type 不受支持。",
        ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
    String correlationId = currentCorrelationId();
    log.error("未处理异常，correlationId={}", correlationId, ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "系统内部错误",
        "系统内部错误，请稍后重试。",
        ErrorCode.INTERNAL_ERROR,
        correlationId);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String errorCode) {
    return problem(status, title, detail, errorCode, currentCorrelationId());
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String errorCode, String correlationId) {
    log.warn("请求失败: errorCode={}, correlationId={}", errorCode, correlationId);
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(title);
    body.setProperty("errorCode", errorCode);
    body.setProperty("correlationId", correlationId);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }

  /** 优先取 {@link CorrelationIdFilter} 写入的 MDC；无 MDC（如直接调用 handler）时兜底生成。 */
  private static String currentCorrelationId() {
    String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    return id != null ? id : UUID.randomUUID().toString();
  }
}
