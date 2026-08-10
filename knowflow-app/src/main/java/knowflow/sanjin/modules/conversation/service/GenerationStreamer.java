package knowflow.sanjin.modules.conversation.service;

import static knowflow.sanjin.common.error.ErrorCode.*;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式 SSE 输出器：把 Provider 的 chunk 流写进 SseEmitter，并负责失败/取消/断连的终结。
 *
 * <p>运行在 {@link GenerationExecutor} 的线程上；通过 {@link GenerationExecutor#isCancelled} 在每个事件前
 * 检查取消标志，保证停止请求能及时被响应并关闭底层流。失败/取消路径会发送 {@link SseEvents#FAILED} 事件再关闭 emitter。
 */
@Component
public class GenerationStreamer {

  private static final Logger log = LoggerFactory.getLogger(GenerationStreamer.class);

  private final GenerationFinalizer finalizer;
  private final ConversationService conversationService;
  private final ModelClientFacade modelClientFacade;
  private final GenerationExecutor executor;

  public GenerationStreamer(
      GenerationFinalizer finalizer,
      ConversationService conversationService,
      ModelClientFacade modelClientFacade,
      GenerationExecutor executor) {
    this.finalizer = finalizer;
    this.conversationService = conversationService;
    this.modelClientFacade = modelClientFacade;
    this.executor = executor;
  }

  /** 在已认领 active slot 的前提下执行生成并把事件写入 emitter。 */
  public void stream(GenerationContext ctx, SseEmitter emitter) throws java.io.IOException {
    StringBuilder content = new StringBuilder();
    AtomicReference<Integer> promptTokens = new AtomicReference<>();
    AtomicReference<Integer> completionTokens = new AtomicReference<>();
    AtomicReference<Integer> totalTokens = new AtomicReference<>();
    long msgId = ctx.assistantMessageId();

    try {
      emitStarted(ctx, emitter);
      emitStage(ctx, emitter, "generating");

      if (executor.isCancelled(msgId)) {
        throw new CancelledGenerationException();
      }

      content.append(
          modelClientFacade.stream(ctx, promptTokens, completionTokens, totalTokens, emitter));

      if (executor.isCancelled(msgId)) {
        throw new CancelledGenerationException();
      }

      boolean active = conversationService.isActiveGeneration(ctx.conversationId(), msgId);
      emitCompleted(
          ctx, emitter, content.toString(), active, promptTokens, completionTokens, totalTokens);
      finalizer.complete(
          ctx.conversationId(),
          msgId,
          content.toString(),
          promptTokens.get(),
          completionTokens.get(),
          totalTokens.get(),
          active);

    } catch (CancelledGenerationException e) {
      log.info("Generation {} cancelled by user", msgId);
      finalizer.cancel(ctx.conversationId(), msgId, content.toString());
      emitFailed(ctx, emitter, GENERATION_CANCELLED, "cancelled", content.toString());

    } catch (RuntimeException e) {
      Throwable cause = rootCause(e);
      String errorCode;
      if (cause instanceof java.io.IOException || cause instanceof java.net.SocketException) {
        log.info("Generation {} client disconnected, finalizing", msgId);
        errorCode = GENERATION_CLIENT_DISCONNECTED;
      } else {
        log.warn("Generation {} failed: {}", msgId, cause.getClass().getSimpleName());
        errorCode = mapErrorCode(cause);
      }
      finalizer.fail(ctx.conversationId(), msgId, content.toString(), errorCode);
      emitFailed(ctx, emitter, errorCode, "failed", content.toString());
    } finally {
      emitter.complete();
    }
  }

  private void emitStarted(GenerationContext ctx, SseEmitter emitter) throws java.io.IOException {
    emitter.send(
        SseEmitter.event()
            .name(SseEvents.STARTED)
            .data(
                new SseEvents.StartedEvent(
                    SseEvents.PROTOCOL_VERSION,
                    Long.toString(ctx.conversationId()),
                    Long.toString(ctx.assistantMessageId()))));
  }

  private void emitStage(GenerationContext ctx, SseEmitter emitter, String stage)
      throws java.io.IOException {
    String modelName =
        ctx.revision() != null && ctx.revision().getModelName() != null
            ? ctx.revision().getModelName()
            : "";
    emitter.send(
        SseEmitter.event()
            .name(SseEvents.STAGE)
            .data(
                new SseEvents.StageEvent(
                    SseEvents.PROTOCOL_VERSION,
                    Long.toString(ctx.assistantMessageId()),
                    stage,
                    modelName)));
  }

  private void emitCompleted(
      GenerationContext ctx,
      SseEmitter emitter,
      String content,
      boolean active,
      AtomicReference<Integer> promptTokens,
      AtomicReference<Integer> completionTokens,
      AtomicReference<Integer> totalTokens)
      throws java.io.IOException {
    emitter.send(
        SseEmitter.event()
            .name(SseEvents.COMPLETED)
            .data(
                new SseEvents.CompletedEvent(
                    SseEvents.PROTOCOL_VERSION,
                    Long.toString(ctx.assistantMessageId()),
                    content,
                    active,
                    new SseEvents.TokenUsage(
                        promptTokens.get(), completionTokens.get(), totalTokens.get()))));
  }

  private void emitFailed(
      GenerationContext ctx, SseEmitter emitter, String errorCode, String stage, String detail)
      throws java.io.IOException {
    // 失败事件：稳定错误码 + 摘要，不透传 Provider 原始错误文本（REVIEW 门禁）
    emitter.send(
        SseEmitter.event()
            .name(SseEvents.FAILED)
            .data(
                new SseEvents.FailedEvent(
                    SseEvents.PROTOCOL_VERSION,
                    Long.toString(ctx.assistantMessageId()),
                    errorCode,
                    stage,
                    detail)));
  }

  private static Throwable rootCause(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    return cur;
  }

  private static String mapErrorCode(Throwable cause) {
    if (cause instanceof java.util.concurrent.TimeoutException) {
      return MODEL_CALL_TIMEOUT;
    }
    // 其他 Provider/网络错误统一归为模型调用失败；不在事件/消息中透传原始错误文本或 API Key
    return MODEL_CALL_FAILED;
  }

  private static final class CancelledGenerationException extends RuntimeException {}
}
