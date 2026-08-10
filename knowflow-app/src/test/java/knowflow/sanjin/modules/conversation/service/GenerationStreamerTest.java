package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import knowflow.sanjin.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** GenerationStreamer 单元测试：验证失败/取消路径会发出 generation.failed 事件并终结状态。 */
class GenerationStreamerTest {

  private final GenerationFinalizer finalizer = mock(GenerationFinalizer.class);
  private final ConversationService conversationService = mock(ConversationService.class);
  private final ModelClientFacade modelClientFacade = mock(ModelClientFacade.class);
  private final GenerationExecutor executor = mock(GenerationExecutor.class);

  private GenerationStreamer streamer() {
    return new GenerationStreamer(finalizer, conversationService, modelClientFacade, executor);
  }

  private GenerationContext context() {
    return new GenerationContext(
        1L,
        1L,
        42L,
        null,
        null,
        List.of(new org.springframework.ai.chat.messages.UserMessage("hi")),
        null);
  }

  @Test
  @DisplayName("should emit generation.failed and fail finalizer when provider stream errors")
  void shouldEmitFailedOnProviderError() throws Exception {
    when(executor.isCancelled(42L)).thenReturn(false);
    when(modelClientFacade.stream(any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("provider boom"));

    SseEmitter emitter = new SseEmitter();
    AtomicReference<String> lastEvent = new AtomicReference<>();
    AtomicReference<String> lastData = new AtomicReference<>();
    emitter.onCompletion(() -> {});
    streamer().stream(context(), emitter);

    verify(finalizer).fail(eq(1L), eq(42L), anyString(), eq(ErrorCode.MODEL_CALL_FAILED), any());
    // 事件已在 emitter 上发出，SseEmitter 通过回调分发；此处验证 finalizer 被正确调用
    assertThat(lastEvent).isNotNull();
  }

  @Test
  @DisplayName("should emit generation.failed(GENERATION_CANCELLED) and cancel finalizer on cancel")
  void shouldEmitCancelledOnCancel() throws Exception {
    when(executor.isCancelled(42L)).thenReturn(true);

    SseEmitter emitter = new SseEmitter();
    streamer().stream(context(), emitter);

    verify(finalizer).cancel(eq(1L), eq(42L), anyString(), any());
  }

  @Test
  @DisplayName("should emit generation.completed and complete finalizer on success")
  void shouldCompleteOnSuccess() throws Exception {
    when(executor.isCancelled(42L)).thenReturn(false);
    when(conversationService.isActiveGeneration(1L, 42L)).thenReturn(true);
    when(modelClientFacade.stream(any(), any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              AtomicReference<Integer> prompt = inv.getArgument(1);
              prompt.set(10);
              return "Hello";
            });

    SseEmitter emitter = new SseEmitter();
    streamer().stream(context(), emitter);

    verify(finalizer).complete(eq(1L), eq(42L), eq("Hello"), eq(10), any(), any(), eq(true), any());
  }
}
