package knowflow.sanjin.modules.modelconfig.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import knowflow.sanjin.common.config.ModelClientProperties;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.modelconfig.exception.ModelCallTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 callWithTotalTimeout 的硬性超时兜底（Future.get(totalTimeout)）。 */
class ModelClientFactoryTimeoutTest {

  private static final byte[] KEY =
      Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY=");

  private ModelClientFactory factory(Duration totalTimeout) {
    ModelClientProperties props = new ModelClientProperties();
    props.setConnectTimeout(Duration.ofSeconds(1));
    props.setReadTimeout(Duration.ofSeconds(2));
    props.setTotalTimeout(totalTimeout);
    props.setAllowLocalBaseUrl(true);
    return new ModelClientFactory(
        new SecretEncryptionService(KEY, 1), new BaseUrlValidator(true), props);
  }

  @Test
  @DisplayName("should throw timeout when supplier exceeds total timeout")
  void shouldEnforceTotalTimeout() {
    ModelClientFactory f = factory(Duration.ofSeconds(1));
    long start = System.currentTimeMillis();
    assertThatThrownBy(
            () ->
                f.callWithTotalTimeout(
                    () -> {
                      try {
                        TimeUnit.SECONDS.sleep(30);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return "late";
                    },
                    1L))
        .isInstanceOf(ModelCallTimeoutException.class);
    long elapsed = System.currentTimeMillis() - start;
    // 应在 total timeout 附近（容忍 3s 缓冲），远小于 30s
    assertThat(elapsed).isLessThan(6_000L);
  }

  @Test
  @DisplayName("should return supplier result when within total timeout")
  void shouldReturnResultWithinTimeout() {
    ModelClientFactory f = factory(Duration.ofSeconds(5));
    String result = f.callWithTotalTimeout(() -> "ok", 1L);
    assertThat(result).isEqualTo("ok");
  }

  @Test
  @DisplayName("should propagate supplier exception")
  void shouldPropagateException() {
    ModelClientFactory f = factory(Duration.ofSeconds(5));
    assertThatThrownBy(
            () ->
                f.callWithTotalTimeout(
                    () -> {
                      throw new IllegalStateException("boom");
                    },
                    1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }
}
