package knowflow.sanjin.modules.conversation.service;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 把 Provider 的流式 chunk 逐个写入 SSE，并累积完整文本与 token usage。
 *
 * <p>阻塞式订阅：{@code Flux.block()} 把整个流压在 {@link GenerationExecutor} 的执行线程上， 复用与 {@code
 * callWithTotalTimeout} 一致的总超时兜底（超时后中断执行线程、终结失败状态）。 {@code doOnNext} 在每次 chunk 到达时写 SSE 事件；客户端断连的
 * {@code IOException} 会沿错误信号 抛给 {@code block()}，由 streamer 统一终结。
 */
@Component
public class ModelClientFacade {

  private static final Logger log = LoggerFactory.getLogger(ModelClientFacade.class);

  /** 返回累积的完整文本；流中途失败/断连时抛出，由调用方终结。 */
  public String stream(
      GenerationContext ctx,
      AtomicReference<Integer> promptTokens,
      AtomicReference<Integer> completionTokens,
      AtomicReference<Integer> totalTokens,
      SseEmitter emitter) {
    ChatModel model = ctx.chatModel();
    StringBuilder collected = new StringBuilder();

    model.stream(new Prompt(ctx.promptMessages()))
        .doOnNext(
            response -> {
              String delta = extractText(response);
              if (delta == null || delta.isEmpty()) {
                return;
              }
              collected.append(delta);
              accumulateUsage(response, promptTokens, completionTokens, totalTokens);
              try {
                emitter.send(
                    SseEmitter.event()
                        .name(SseEvents.DELTA)
                        .data(
                            new SseEvents.DeltaEvent(
                                SseEvents.PROTOCOL_VERSION,
                                Long.toString(ctx.assistantMessageId()),
                                delta)));
              } catch (java.io.IOException e) {
                throw new RuntimeException("sse-write-failed", e);
              }
            })
        .blockLast();

    return collected.toString();
  }

  private static String extractText(ChatResponse response) {
    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
      return "";
    }
    Generation generation = response.getResult();
    if (generation == null || generation.getOutput() == null) {
      return "";
    }
    return generation.getOutput().getText();
  }

  private static void accumulateUsage(
      ChatResponse response,
      AtomicReference<Integer> promptTokens,
      AtomicReference<Integer> completionTokens,
      AtomicReference<Integer> totalTokens) {
    if (response == null || response.getMetadata() == null) {
      return;
    }
    var usage = response.getMetadata().getUsage();
    if (usage == null) {
      return;
    }
    if (usage.getPromptTokens() != null) {
      promptTokens.set(usage.getPromptTokens());
    }
    if (usage.getCompletionTokens() != null) {
      completionTokens.set(usage.getCompletionTokens());
    }
    if (usage.getTotalTokens() != null) {
      totalTokens.set(usage.getTotalTokens());
    }
  }
}
