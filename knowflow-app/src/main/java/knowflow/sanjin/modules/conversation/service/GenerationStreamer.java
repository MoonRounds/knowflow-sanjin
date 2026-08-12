package knowflow.sanjin.modules.conversation.service;

import static knowflow.sanjin.common.error.ErrorCode.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import knowflow.sanjin.modules.conversation.title.ConversationTitleService;
import knowflow.sanjin.modules.rag.dto.RagContext;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import knowflow.sanjin.modules.rag.dto.RouterResult;
import knowflow.sanjin.modules.rag.dto.RouterTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流式 SSE 输出器：把 Provider 的 chunk 流写进 SseEmitter，并负责失败/取消/断连的终结。
 *
 * <p>运行在 {@link GenerationExecutor} 的线程上；通过 {@link GenerationExecutor#isCancelled} 在每个事件前
 * 检查取消标志，保证停止请求能及时被响应并关闭底层流。失败/取消路径会发送 {@link SseEvents#FAILED} 事件再关闭 emitter。 流式结束后解析全文 {@code
 * [Sx]} 引用，发送 {@link SseEvents#SOURCES_AVAILABLE} 后发送 completed。
 */
@Component
public class GenerationStreamer {

  private static final Logger log = LoggerFactory.getLogger(GenerationStreamer.class);

  private final GenerationFinalizer finalizer;
  private final ConversationService conversationService;
  private final ModelClientFacade modelClientFacade;
  private final GenerationExecutor executor;
  private final ConversationTitleService conversationTitleService;

  public GenerationStreamer(
      GenerationFinalizer finalizer,
      ConversationService conversationService,
      ModelClientFacade modelClientFacade,
      GenerationExecutor executor,
      ConversationTitleService conversationTitleService) {
    this.finalizer = finalizer;
    this.conversationService = conversationService;
    this.modelClientFacade = modelClientFacade;
    this.executor = executor;
    this.conversationTitleService = conversationTitleService;
  }

  /** 在已认领 active slot 的前提下执行生成并把事件写入 emitter。 */
  public void stream(GenerationContext ctx, SseEmitter emitter) throws java.io.IOException {
    StringBuilder content = new StringBuilder();
    AtomicReference<Integer> promptTokens = new AtomicReference<>();
    AtomicReference<Integer> completionTokens = new AtomicReference<>();
    AtomicReference<Integer> totalTokens = new AtomicReference<>();
    long msgId = ctx.assistantMessageId();
    GenerationTraceSnapshot snapshot = GenerationTraceSnapshot.from(ctx.ragContext());

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
      // 流式结束后解析 cited，并在对客户端宣告 completed 前先提交 MySQL Tx2、释放 active slot。
      List<RetrievedSource> cited = markCited(ctx.ragContext(), content.toString(), snapshot);
      finalizer.complete(
          ctx.conversationId(),
          msgId,
          content.toString(),
          promptTokens.get(),
          completionTokens.get(),
          totalTokens.get(),
          active,
          snapshot);
      triggerTitleGeneration(ctx);
      emitSourcesAvailable(ctx, emitter, cited, snapshot);
      emitCompleted(
          ctx,
          emitter,
          content.toString(),
          active,
          snapshot,
          promptTokens,
          completionTokens,
          totalTokens);

    } catch (CancelledGenerationException e) {
      log.info("Generation {} cancelled by user", msgId);
      finalizer.cancel(ctx.conversationId(), msgId, content.toString(), snapshot);
      emitFailed(ctx, emitter, GENERATION_CANCELLED, "cancelled", "生成已取消");

    } catch (java.io.IOException e) {
      // emitter.send 可直接抛 IOException（客户端断开）；不能只由上层记录日志，否则消息与
      // conversation active slot 会永久停留在 GENERATING。
      if (executor.isCancelled(msgId)) {
        // stop 请求返回后客户端会主动关闭 SSE；此时写事件可能与取消终结并发抛 IOException。
        // 用户取消标志优先，避免同一次操作被错误归类为客户端断连。
        log.info("Generation {} cancelled while closing SSE", msgId);
        finalizer.cancel(ctx.conversationId(), msgId, content.toString(), snapshot);
      } else {
        log.info("Generation {} client disconnected while emitting, finalizing", msgId);
        finalizer.fail(
            ctx.conversationId(),
            msgId,
            content.toString(),
            GENERATION_CLIENT_DISCONNECTED,
            snapshot);
      }

    } catch (RuntimeException e) {
      if (executor.isCancelled(msgId)) {
        // stop 请求会中断底层流，Provider 客户端通常以 RuntimeException 结束；取消标志优先于
        // Provider 错误分类，确保用户停止稳定落为 CANCELLED。
        log.info("Generation {} cancelled while provider stream was active", msgId);
        finalizer.cancel(ctx.conversationId(), msgId, content.toString(), snapshot);
        emitFailed(ctx, emitter, GENERATION_CANCELLED, "cancelled", "生成已取消");
      } else {
        Throwable cause = rootCause(e);
        String errorCode;
        String detail;
        if (cause instanceof java.io.IOException || cause instanceof java.net.SocketException) {
          log.info("Generation {} client disconnected, finalizing", msgId);
          errorCode = GENERATION_CLIENT_DISCONNECTED;
          detail = "客户端已断开";
        } else {
          errorCode = mapErrorCode(cause);
          detail = errorCode;
          // 真实失败（超时/模型/未知）记 error 级并带完整堆栈，供控制台定位问题
          log.error("生成 {} 失败: errorCode={}", msgId, errorCode, e);
        }
        finalizer.fail(ctx.conversationId(), msgId, content.toString(), errorCode, snapshot);
        emitFailed(ctx, emitter, errorCode, "failed", detail);
      }
    } finally {
      emitter.complete();
    }
  }

  /** 首轮回答完成后触发会话标题 AI 生成（旁路）：失败仅记日志，不影响生成与 SSE 事件。 */
  private void triggerTitleGeneration(GenerationContext ctx) {
    try {
      conversationTitleService.ensureTitleTask(ctx.conversationId());
    } catch (RuntimeException e) {
      log.warn("触发会话标题生成失败 conversation={}", ctx.conversationId(), e);
    }
  }

  private static List<RetrievedSource> markCited(
      RagContext rag, String content, GenerationTraceSnapshot snapshot) {
    if (rag == null || rag.getSources() == null || rag.getSources().isEmpty()) {
      return List.of();
    }
    List<RetrievedSource> sources = CitationParser.markCited(rag.getSources(), content);
    // 快照也要携带 cited 标记（trace 落库用）
    if (snapshot != null && snapshot.sources() != null) {
      for (RetrievedSource s : sources) {
        snapshot.sources().stream()
            .filter(x -> x.getSourceId().equals(s.getSourceId()))
            .findFirst()
            .ifPresent(x -> x.setCited(s.isCited()));
      }
    }
    return sources;
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

  private void emitSourcesAvailable(
      GenerationContext ctx,
      SseEmitter emitter,
      List<RetrievedSource> sources,
      GenerationTraceSnapshot snapshot)
      throws java.io.IOException {
    RagContext rag = ctx.ragContext();
    String ragStatus = rag != null ? rag.getRagStatus() : null;
    SseEvents.RouterDiagnostic router = toDiagnostic(snapshot);
    emitter.send(
        SseEmitter.event()
            .name(SseEvents.SOURCES_AVAILABLE)
            .data(
                new SseEvents.SourcesAvailableEvent(
                    SseEvents.PROTOCOL_VERSION,
                    Long.toString(ctx.assistantMessageId()),
                    ragStatus,
                    sources,
                    router)));
  }

  private SseEvents.RouterDiagnostic toDiagnostic(GenerationTraceSnapshot snapshot) {
    if (snapshot == null || snapshot.routerTrace() == null) {
      return null;
    }
    RouterTrace trace = snapshot.routerTrace();
    if (!trace.isRouterCalled() || trace.getResult() == null) {
      return null;
    }
    RouterResult result = trace.getResult();
    return new SseEvents.RouterDiagnostic(
        result.isNeedRag(),
        result.getKnowledgeBaseIds() != null ? result.getKnowledgeBaseIds() : List.of(),
        result.getRetrievalQuery(),
        result.getRouteScores() != null ? result.getRouteScores() : List.of());
  }

  private void emitCompleted(
      GenerationContext ctx,
      SseEmitter emitter,
      String content,
      boolean active,
      GenerationTraceSnapshot snapshot,
      AtomicReference<Integer> promptTokens,
      AtomicReference<Integer> completionTokens,
      AtomicReference<Integer> totalTokens)
      throws java.io.IOException {
    RagContext rag = ctx.ragContext();
    String ragStatus = rag != null ? rag.getRagStatus() : null;
    emitter.send(
        SseEmitter.event()
            .name(SseEvents.COMPLETED)
            .data(
                new SseEvents.CompletedEvent(
                    SseEvents.PROTOCOL_VERSION,
                    Long.toString(ctx.assistantMessageId()),
                    content,
                    active,
                    ragStatus,
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
