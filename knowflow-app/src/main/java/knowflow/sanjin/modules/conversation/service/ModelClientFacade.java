package knowflow.sanjin.modules.conversation.service;

import java.util.concurrent.atomic.AtomicReference;
import knowflow.sanjin.common.util.ObsLog;
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
 * callWithTotalTimeout} 一致的总超时兜底（超时后中断执行线程、终结失败状态）。 {@code doOnNext} 在每次 chunk 到达时经 {@link
 * SilentSseWriter} 写 SSE 事件：客户端断连被 writer 静默降级，不再沿错误信号中断 Provider 流， 生成继续在后台完成。
 */
@Component
public class ModelClientFacade {

  private static final Logger log = LoggerFactory.getLogger(ModelClientFacade.class);

  /** 返回累积的完整文本；Provider 流失败时抛出，由调用方终结。 */
  public String stream(
      GenerationContext ctx,
      AtomicReference<Integer> promptTokens,
      AtomicReference<Integer> completionTokens,
      AtomicReference<Integer> totalTokens,
      SilentSseWriter writer) {
    ChatModel model = ctx.chatModel();
    StringBuilder collected = new StringBuilder();
    long start = System.nanoTime();
    String modelName =
        ctx.revision() != null && ctx.revision().getModelName() != null
            ? ctx.revision().getModelName()
            : "unknown";

    model.stream(new Prompt(ctx.promptMessages()))
        .doOnNext(
            response -> {
              String delta = extractText(response);
              if (delta == null || delta.isEmpty()) {
                return;
              }
              collected.append(delta);
              accumulateUsage(response, promptTokens, completionTokens, totalTokens);
              writer.send(
                  SseEmitter.event()
                      .name(SseEvents.DELTA)
                      .data(
                          new SseEvents.DeltaEvent(
                              SseEvents.PROTOCOL_VERSION,
                              Long.toString(ctx.assistantMessageId()),
                              delta)));
            })
        .blockLast();

    String fullText = collected.toString();
    log.info(
        "LLM流式生成完成 messageId={} 模型={} 耗时={} 输出字符数={} 输入Token={} 输出Token={} 总Token={}",
        ctx.assistantMessageId(),
        modelName,
        ObsLog.elapsedMs(start),
        fullText.length(),
        promptTokens.get(),
        completionTokens.get(),
        totalTokens.get());
    return fullText;
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
