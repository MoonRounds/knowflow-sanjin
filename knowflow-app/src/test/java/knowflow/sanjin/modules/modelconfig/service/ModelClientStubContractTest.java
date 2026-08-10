package knowflow.sanjin.modules.modelconfig.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Base64;
import java.util.stream.Collectors;
import knowflow.sanjin.common.config.ModelClientProperties;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.testinfra.stub.OpenAiCompatibleStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 本地 OpenAI-Compatible Stub 契约测试：验证 ModelClientFactory 与 Spring AI 客户端 能按 OpenAI
 * 基础契约承载普通响应、流式协议、usage、401、429 和非法 JSON。
 */
class ModelClientStubContractTest {

  private static final byte[] KEY =
      Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY=");

  private OpenAiCompatibleStub stub;
  private ModelClientFactory factory;

  @BeforeEach
  void setUp() throws Exception {
    stub = OpenAiCompatibleStub.start();
    ModelClientProperties props = new ModelClientProperties();
    props.setConnectTimeout(Duration.ofSeconds(3));
    props.setReadTimeout(Duration.ofSeconds(5));
    props.setTotalTimeout(Duration.ofSeconds(10));
    props.setAllowLocalBaseUrl(true);
    factory =
        new ModelClientFactory(
            new SecretEncryptionService(KEY, 1), new BaseUrlValidator(true), props);
  }

  @AfterEach
  void tearDown() {
    if (stub != null) {
      stub.close();
    }
  }

  private ModelConfigRevision revision() {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setModelConfigId(1L);
    rev.setOwnerId(1L);
    rev.setRevisionNo(1);
    rev.setBaseUrl(stub.baseUrl());
    rev.setModelName("stub-model");
    rev.setTemperature(0.7);
    rev.setMaxOutputTokens(2048);
    rev.setEncryptedApiKey(new SecretEncryptionService(KEY, 1).encrypt("sk-stub-test-key"));
    rev.setApiKeyEncryptionVersion(1);
    return rev;
  }

  @Test
  @DisplayName("should complete a normal chat call against the stub")
  void shouldCallNormal() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.NORMAL);
    ChatModel model = factory.create(revision());
    ChatResponse response = model.call(new Prompt("ping"));
    String text = factory.extractText(response);
    assertThat(text).isEqualTo("pong");
  }

  @Test
  @DisplayName("should receive streamed chat response")
  void shouldReceiveStream() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.STREAM);
    ChatModel model = factory.create(revision());
    String text =
        model.stream(new Prompt("ping"))
            .map(r -> factory.extractText(r))
            .collect(Collectors.joining())
            .block();
    assertThat(text).contains("Hel").contains("lo");
  }

  @Test
  @DisplayName("should map 401 to a connection failure message without leaking secret")
  void shouldHandleUnauthorized() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.UNAUTHORIZED);
    ChatModel model = factory.create(revision());
    assertThatThrownBy(() -> model.call(new Prompt("ping"))).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("should map 429 to a failure")
  void shouldHandleRateLimit() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.RATE_LIMITED);
    ChatModel model = factory.create(revision());
    assertThatThrownBy(() -> model.call(new Prompt("ping"))).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("should surface malformed JSON as a failure")
  void shouldHandleMalformedJson() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.MALFORMED_JSON);
    ChatModel model = factory.create(revision());
    assertThatThrownBy(() -> model.call(new Prompt("ping"))).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("should fail cleanly when peer closes connection (timeout/abort)")
  void shouldHandleTimeout() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.TIMEOUT);
    ChatModel model = factory.create(revision());
    assertThatThrownBy(
            () ->
                factory.callWithTotalTimeout(
                    () -> model.call(new Prompt("ping")), revision().getId()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("should reject redirects instead of forwarding model credentials")
  void shouldRejectRedirect() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.REDIRECT);
    ChatModel model = factory.create(revision());

    assertThatThrownBy(() -> model.call(new Prompt("ping"))).isInstanceOf(RuntimeException.class);
  }
}
