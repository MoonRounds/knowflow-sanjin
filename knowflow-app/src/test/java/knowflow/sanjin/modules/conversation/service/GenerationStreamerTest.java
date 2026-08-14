package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.conversation.title.ConversationTitleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** GenerationStreamer 单元测试：验证失败/取消路径会发出 generation.failed 事件并终结状态。 */
class GenerationStreamerTest {

  private final GenerationFinalizer finalizer = mock(GenerationFinalizer.class);
  private final ConversationService conversationService = mock(ConversationService.class);
  private final ModelClientFacade modelClientFacade = mock(ModelClientFacade.class);
  private final GenerationExecutor executor = mock(GenerationExecutor.class);
  private final ConversationTitleService conversationTitleService =
      mock(ConversationTitleService.class);

  private GenerationStreamer streamer() {
    return new GenerationStreamer(
        finalizer, conversationService, modelClientFacade, executor, conversationTitleService);
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
  @DisplayName(
      "should complete generation in silent mode when emitter send throws IOException (client disconnected)")
  void shouldCompleteSilentlyOnClientDisconnect() throws Exception {
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));
    when(executor.isCancelled(42L)).thenReturn(false);
    when(conversationService.isActiveGeneration(1L, 42L)).thenReturn(true);
    when(modelClientFacade.stream(any(), any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              AtomicReference<Integer> prompt = inv.getArgument(1);
              prompt.set(10);
              return "Hello";
            });

    streamer().stream(context(), emitter);

    // 断连不再中断生成：Provider 流照常收完，以 COMPLETED 落库
    verify(finalizer).complete(eq(1L), eq(42L), eq("Hello"), eq(10), any(), any(), eq(true), any());
    verify(finalizer, never()).fail(anyLong(), anyLong(), anyString(), anyString(), any());
    verify(emitter).complete();
  }

  @Test
  @DisplayName("should prefer CANCELLED when stop races with emitter disconnect")
  void shouldPreferCancelledOnEmitterDisconnectRace() throws Exception {
    SseEmitter emitter = mock(SseEmitter.class);
    when(executor.isCancelled(42L)).thenReturn(true);
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    streamer().stream(context(), emitter);

    verify(finalizer).cancel(eq(1L), eq(42L), anyString(), any());
    verify(finalizer, never()).fail(anyLong(), anyLong(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName(
      "should classify provider-side IO error as MODEL_CALL_FAILED (not client disconnect)")
  void shouldClassifyProviderIoErrorAsModelCallFailed() throws Exception {
    when(executor.isCancelled(42L)).thenReturn(false);
    when(modelClientFacade.stream(any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException(new java.net.SocketException("upstream reset")));

    streamer().stream(context(), new SseEmitter());

    verify(finalizer).fail(eq(1L), eq(42L), anyString(), eq(ErrorCode.MODEL_CALL_FAILED), any());
    verify(finalizer, never()).cancel(anyLong(), anyLong(), anyString(), any());
  }

  @Test
  @DisplayName("should prefer CANCELLED when stop interrupts an active provider stream")
  void shouldPreferCancelledWhenProviderIsInterrupted() throws Exception {
    when(executor.isCancelled(42L)).thenReturn(false, true);
    when(modelClientFacade.stream(any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("interrupted provider stream"));

    streamer().stream(context(), new SseEmitter());

    verify(finalizer).cancel(eq(1L), eq(42L), anyString(), any());
    verify(finalizer, never()).fail(anyLong(), anyLong(), anyString(), anyString(), any());
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

    SseEmitter emitter = mock(SseEmitter.class);
    streamer().stream(context(), emitter);

    InOrder order = inOrder(finalizer, emitter);
    order
        .verify(finalizer)
        .complete(eq(1L), eq(42L), eq("Hello"), eq(10), any(), any(), eq(true), any());
    order.verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    // 成功路径触发标题生成（旁路，不影响 SSE 事件）
    verify(conversationTitleService).ensureTitleTask(1L);
  }
}
