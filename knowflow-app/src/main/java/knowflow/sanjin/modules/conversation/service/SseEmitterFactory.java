package knowflow.sanjin.modules.conversation.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 创建带超时的 SseEmitter 的小工厂。 */
@Component
public class SseEmitterFactory {

  private final GenerationProperties properties;

  public SseEmitterFactory(GenerationProperties properties) {
    this.properties = properties;
  }

  public SseEmitter create() {
    return new SseEmitter(properties.getTotalTimeout().toMillis());
  }

  /** 幂等重复请求时返回的空流（客户端立即收到 EOF）。 */
  public SseEmitter createEmpty() {
    SseEmitter emitter = new SseEmitter(0L);
    emitter.complete();
    return emitter;
  }
}
