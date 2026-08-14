package knowflow.sanjin.modules.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 客户端断连后自动降级的 SSE 写入器。
 *
 * <p>第一次写入失败（客户端断开）后进入静默模式：后续事件不再发送、也不再抛异常，让 Provider 流 正常跑完并以 COMPLETED
 * 落库。用户切换模块/切换会话导致的断连因此不再打断生成，回答在后台完成后 可通过重新拉取历史获得。静默状态由 {@link #isDisconnected()} 暴露，供终结日志标注。
 */
public final class SilentSseWriter {

  private static final Logger log = LoggerFactory.getLogger(SilentSseWriter.class);

  private final SseEmitter emitter;
  private volatile boolean disconnected;

  public SilentSseWriter(SseEmitter emitter) {
    this.emitter = emitter;
  }

  /** 发送事件；客户端已断开后为 no-op，不抛出。 */
  public void send(SseEmitter.SseEventBuilder event) {
    if (disconnected) {
      return;
    }
    try {
      emitter.send(event);
    } catch (java.io.IOException e) {
      disconnected = true;
      log.info("SSE 客户端断开，进入静默模式：生成继续在后台完成并落库（{}）", e.getMessage());
    }
  }

  /** 是否已检测到客户端断开（进入静默模式）。 */
  public boolean isDisconnected() {
    return disconnected;
  }
}
