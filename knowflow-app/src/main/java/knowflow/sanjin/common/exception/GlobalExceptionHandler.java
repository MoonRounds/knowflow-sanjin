package knowflow.sanjin.common.exception;

import java.util.UUID;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.exception.MessageNotFoundException;
import knowflow.sanjin.modules.conversation.exception.NoDefaultModelConfigException;
import knowflow.sanjin.modules.extraction.exception.CandidateEmptyDraftException;
import knowflow.sanjin.modules.extraction.exception.CandidateInvalidStateException;
import knowflow.sanjin.modules.extraction.exception.CandidateNoKnowledgeBaseException;
import knowflow.sanjin.modules.extraction.exception.CandidateNotFoundException;
import knowflow.sanjin.modules.extraction.exception.CandidateVersionConflictException;
import knowflow.sanjin.modules.extraction.exception.ExtractionInputOverBudgetException;
import knowflow.sanjin.modules.extraction.exception.ExtractionNoCompletedMessagesException;
import knowflow.sanjin.modules.extraction.exception.ExtractionTaskNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeBaseRefNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeIndexTaskConflictException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemVersionConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseInUseException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNameConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.modelconfig.exception.ModelCallTimeoutException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigDisabledException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigInUseException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigRevisionChangedException;
import knowflow.sanjin.modules.modelconfig.exception.UtilityCapabilityRequiredException;
import knowflow.sanjin.modules.processing.exception.ProcessingTaskNotFoundException;
import knowflow.sanjin.modules.processing.exception.ProcessingTaskRetryNotAllowedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常 → RFC 9457 Problem Details 转换：稳定 errorCode + correlationId。
 *
 * <p>业务异常映射为稳定错误码；通用异常（校验失败、非法参数）归类为 400；未知异常记日志并返回 通用 500，不透传内部细节。correlationId 贯穿日志与响应便于排查。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(KnowledgeBaseNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(KnowledgeBaseNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "KnowledgeBase not found",
        "KnowledgeBase with id=" + ex.getId() + " does not exist or is not accessible.",
        ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
  }

  @ExceptionHandler(KnowledgeBaseNameConflictException.class)
  public ResponseEntity<ProblemDetail> handleNameConflict(KnowledgeBaseNameConflictException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Name conflict",
        "An active KnowledgeBase with the same normalized name already exists.",
        ErrorCode.KNOWLEDGE_BASE_NAME_CONFLICT);
  }

  @ExceptionHandler(KnowledgeBaseVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleVersionConflict(
      KnowledgeBaseVersionConflictException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Version conflict",
        ex.getMessage(),
        ErrorCode.KNOWLEDGE_BASE_VERSION_CONFLICT);
  }

  @ExceptionHandler(KnowledgeBaseInUseException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeBaseInUse(KnowledgeBaseInUseException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "KnowledgeBase in use",
        ex.getMessage(),
        ErrorCode.KNOWLEDGE_BASE_IN_USE);
  }

  @ExceptionHandler(KnowledgeItemNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeItemNotFound(
      KnowledgeItemNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "KnowledgeItem not found",
        "KnowledgeItem with id=" + ex.getId() + " does not exist or is not accessible.",
        ErrorCode.KNOWLEDGE_ITEM_NOT_FOUND);
  }

  @ExceptionHandler(KnowledgeBaseRefNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeBaseRefNotFound(
      KnowledgeBaseRefNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "KnowledgeBase not found",
        "Referenced KnowledgeBase with id="
            + ex.getKnowledgeBaseId()
            + " does not exist or is not accessible.",
        ErrorCode.KNOWLEDGE_BASE_REF_NOT_FOUND);
  }

  @ExceptionHandler(KnowledgeItemVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeItemVersionConflict(
      KnowledgeItemVersionConflictException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Version conflict",
        ex.getMessage(),
        ErrorCode.KNOWLEDGE_ITEM_VERSION_CONFLICT);
  }

  @ExceptionHandler(KnowledgeIndexTaskConflictException.class)
  public ResponseEntity<ProblemDetail> handleKnowledgeIndexTaskConflict(
      KnowledgeIndexTaskConflictException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Index task conflict",
        ex.getMessage(),
        ErrorCode.KNOWLEDGE_INDEX_TASK_CONFLICT);
  }

  @ExceptionHandler(ProcessingTaskNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleProcessingTaskNotFound(
      ProcessingTaskNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "ProcessingTask not found",
        "ProcessingTask with id=" + ex.getId() + " does not exist or is not accessible.",
        ErrorCode.PROCESSING_TASK_NOT_FOUND);
  }

  @ExceptionHandler(ProcessingTaskRetryNotAllowedException.class)
  public ResponseEntity<ProblemDetail> handleProcessingTaskRetryNotAllowed(
      ProcessingTaskRetryNotAllowedException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Retry not allowed",
        ex.getMessage(),
        ErrorCode.PROCESSING_TASK_RETRY_NOT_ALLOWED);
  }

  @ExceptionHandler(ModelConfigNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigNotFound(ModelConfigNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "ModelConfig not found",
        "ModelConfig with id=" + ex.getId() + " does not exist or is not accessible.",
        ErrorCode.MODEL_CONFIG_NOT_FOUND);
  }

  @ExceptionHandler(ModelConfigDisabledException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigDisabled(ModelConfigDisabledException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "ModelConfig disabled",
        "ModelConfig with id=" + ex.getId() + " is disabled and cannot be selected.",
        ErrorCode.MODEL_CONFIG_DISABLED);
  }

  @ExceptionHandler(ModelConfigInUseException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigInUse(ModelConfigInUseException ex) {
    return problem(
        HttpStatus.CONFLICT, "ModelConfig in use", ex.getMessage(), ErrorCode.MODEL_CONFIG_IN_USE);
  }

  @ExceptionHandler(UtilityCapabilityRequiredException.class)
  public ResponseEntity<ProblemDetail> handleUtilityCapabilityRequired(
      UtilityCapabilityRequiredException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Utility capability test required",
        ex.getMessage(),
        ErrorCode.UTILITY_CAPABILITY_TEST_REQUIRED);
  }

  @ExceptionHandler(ModelConfigRevisionChangedException.class)
  public ResponseEntity<ProblemDetail> handleRevisionChanged(
      ModelConfigRevisionChangedException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "ModelConfig revision changed",
        ex.getMessage(),
        ErrorCode.MODEL_CONFIG_REVISION_CHANGED);
  }

  @ExceptionHandler(ModelCallTimeoutException.class)
  public ResponseEntity<ProblemDetail> handleModelCallTimeout(ModelCallTimeoutException ex) {
    return problem(
        HttpStatus.GATEWAY_TIMEOUT,
        "Model call timeout",
        "The model call did not complete within the allowed time.",
        ErrorCode.MODEL_CALL_TIMEOUT);
  }

  @ExceptionHandler(ConversationNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleConversationNotFound(
      ConversationNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Conversation not found",
        "Conversation with id=" + ex.getId() + " does not exist or is not accessible.",
        ErrorCode.CONVERSATION_NOT_FOUND);
  }

  @ExceptionHandler(MessageNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleMessageNotFound(MessageNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Message not found",
        "Message with id="
            + ex.getMessageId()
            + " does not exist in conversation "
            + ex.getConversationId()
            + ".",
        ErrorCode.MESSAGE_NOT_FOUND);
  }

  @ExceptionHandler(ActiveGenerationExistsException.class)
  public ResponseEntity<ProblemDetail> handleActiveGenerationExists(
      ActiveGenerationExistsException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Active generation exists",
        ex.getMessage(),
        ErrorCode.ACTIVE_GENERATION_EXISTS);
  }

  @ExceptionHandler(NoDefaultModelConfigException.class)
  public ResponseEntity<ProblemDetail> handleNoDefaultModelConfig(
      NoDefaultModelConfigException ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "No default model config",
        "No default chat model is configured. Please select a model before sending a message.",
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
        HttpStatus.NOT_FOUND,
        "提取任务不存在",
        "Extraction task with id=" + ex.getId() + " does not exist or is not accessible.",
        ex.getErrorCode());
  }

  @ExceptionHandler(CandidateNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleCandidateNotFound(CandidateNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "候选不存在",
        "Candidate with id=" + ex.getId() + " does not exist or is not accessible.",
        ex.getErrorCode());
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
        HttpStatus.PRECONDITION_REQUIRED,
        "Precondition required",
        ex.getMessage(),
        ErrorCode.IF_MATCH_REQUIRED);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid argument", ex.getMessage(), ErrorCode.INVALID_ARGUMENT);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Validation failed");
    return problem(HttpStatus.BAD_REQUEST, "Validation error", detail, ErrorCode.VALIDATION_ERROR);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
    String correlationId = UUID.randomUUID().toString();
    log.error("Unhandled exception, correlationId={}", correlationId, ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "An unexpected error occurred. Please try again later.",
        ErrorCode.INTERNAL_ERROR,
        correlationId);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String errorCode) {
    return problem(status, title, detail, errorCode, UUID.randomUUID().toString());
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String errorCode, String correlationId) {
    log.warn("Request failed: errorCode={}, correlationId={}", errorCode, correlationId);
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setTitle(title);
    body.setProperty("errorCode", errorCode);
    body.setProperty("correlationId", correlationId);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }
}
