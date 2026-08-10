package knowflow.sanjin.common.exception;

import java.util.UUID;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.exception.MessageNotFoundException;
import knowflow.sanjin.modules.conversation.exception.NoDefaultModelConfigException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNameConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.modelconfig.exception.ModelCallTimeoutException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigDisabledException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigInUseException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigRevisionChangedException;
import knowflow.sanjin.modules.modelconfig.exception.UtilityCapabilityRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(KnowledgeBaseNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(KnowledgeBaseNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "KnowledgeBase not found",
        "KnowledgeBase with id=" + ex.getId() + " does not exist or is not accessible.",
        "KNOWLEDGE_BASE_NOT_FOUND");
  }

  @ExceptionHandler(KnowledgeBaseNameConflictException.class)
  public ResponseEntity<ProblemDetail> handleNameConflict(KnowledgeBaseNameConflictException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Name conflict",
        "An active KnowledgeBase with the same normalized name already exists.",
        "KNOWLEDGE_BASE_NAME_CONFLICT");
  }

  @ExceptionHandler(KnowledgeBaseVersionConflictException.class)
  public ResponseEntity<ProblemDetail> handleVersionConflict(
      KnowledgeBaseVersionConflictException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Version conflict",
        ex.getMessage(),
        "KNOWLEDGE_BASE_VERSION_CONFLICT");
  }

  @ExceptionHandler(ModelConfigNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigNotFound(ModelConfigNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "ModelConfig not found",
        "ModelConfig with id=" + ex.getId() + " does not exist or is not accessible.",
        "MODEL_CONFIG_NOT_FOUND");
  }

  @ExceptionHandler(ModelConfigDisabledException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigDisabled(ModelConfigDisabledException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "ModelConfig disabled",
        "ModelConfig with id=" + ex.getId() + " is disabled and cannot be selected.",
        "MODEL_CONFIG_DISABLED");
  }

  @ExceptionHandler(ModelConfigInUseException.class)
  public ResponseEntity<ProblemDetail> handleModelConfigInUse(ModelConfigInUseException ex) {
    return problem(
        HttpStatus.CONFLICT, "ModelConfig in use", ex.getMessage(), "MODEL_CONFIG_IN_USE");
  }

  @ExceptionHandler(UtilityCapabilityRequiredException.class)
  public ResponseEntity<ProblemDetail> handleUtilityCapabilityRequired(
      UtilityCapabilityRequiredException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Utility capability test required",
        ex.getMessage(),
        "UTILITY_CAPABILITY_TEST_REQUIRED");
  }

  @ExceptionHandler(ModelConfigRevisionChangedException.class)
  public ResponseEntity<ProblemDetail> handleRevisionChanged(
      ModelConfigRevisionChangedException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "ModelConfig revision changed",
        ex.getMessage(),
        "MODEL_CONFIG_REVISION_CHANGED");
  }

  @ExceptionHandler(ModelCallTimeoutException.class)
  public ResponseEntity<ProblemDetail> handleModelCallTimeout(ModelCallTimeoutException ex) {
    return problem(
        HttpStatus.GATEWAY_TIMEOUT,
        "Model call timeout",
        "The model call did not complete within the allowed time.",
        "MODEL_CALL_TIMEOUT");
  }

  @ExceptionHandler(ConversationNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleConversationNotFound(
      ConversationNotFoundException ex) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Conversation not found",
        "Conversation with id=" + ex.getId() + " does not exist or is not accessible.",
        "CONVERSATION_NOT_FOUND");
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
        "MESSAGE_NOT_FOUND");
  }

  @ExceptionHandler(ActiveGenerationExistsException.class)
  public ResponseEntity<ProblemDetail> handleActiveGenerationExists(
      ActiveGenerationExistsException ex) {
    return problem(
        HttpStatus.CONFLICT,
        "Active generation exists",
        ex.getMessage(),
        "ACTIVE_GENERATION_EXISTS");
  }

  @ExceptionHandler(NoDefaultModelConfigException.class)
  public ResponseEntity<ProblemDetail> handleNoDefaultModelConfig(
      NoDefaultModelConfigException ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "No default model config",
        "No default chat model is configured. Please select a model before sending a message.",
        "NO_DEFAULT_MODEL_CONFIG");
  }

  @ExceptionHandler(PreconditionRequiredException.class)
  public ResponseEntity<ProblemDetail> handlePreconditionRequired(
      PreconditionRequiredException ex) {
    return problem(
        HttpStatus.PRECONDITION_REQUIRED,
        "Precondition required",
        ex.getMessage(),
        "IF_MATCH_REQUIRED");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid argument", ex.getMessage(), "INVALID_ARGUMENT");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Validation failed");
    return problem(HttpStatus.BAD_REQUEST, "Validation error", detail, "VALIDATION_ERROR");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
    String correlationId = UUID.randomUUID().toString();
    log.error("Unhandled exception, correlationId={}", correlationId, ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "An unexpected error occurred. Please try again later.",
        "INTERNAL_ERROR",
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
