package knowflow.sanjin.modules.modelconfig.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Base64;
import knowflow.sanjin.common.config.ModelClientProperties;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

/** ModelClientFactory 单元测试：按 Revision 创建 OpenAI-Compatible 客户端并承载基础参数。 */
class ModelClientFactoryTest {

  private static final byte[] KEY =
      Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY=");

  private ModelClientFactory factory(boolean allowLocal) {
    ModelClientProperties props = new ModelClientProperties();
    props.setConnectTimeout(Duration.ofSeconds(5));
    props.setReadTimeout(Duration.ofSeconds(30));
    props.setTotalTimeout(Duration.ofSeconds(45));
    props.setAllowLocalBaseUrl(allowLocal);
    return new ModelClientFactory(
        new SecretEncryptionService(KEY, 1), new BaseUrlValidator(allowLocal), props);
  }

  private ModelConfigRevision revision(String baseUrl, String model, String apiKey) {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setModelConfigId(1L);
    rev.setOwnerId(1L);
    rev.setRevisionNo(1);
    rev.setBaseUrl(baseUrl);
    rev.setModelName(model);
    rev.setTemperature(0.7);
    rev.setMaxOutputTokens(2048);
    rev.setEncryptedApiKey(new SecretEncryptionService(KEY, 1).encrypt(apiKey));
    rev.setApiKeyEncryptionVersion(1);
    return rev;
  }

  @Test
  @DisplayName("should create a chat model from revision when base url allowed")
  void shouldCreateModel() {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setBaseUrl("https://1.1.1.1/v1");
    rev.setModelName("deepseek-chat");
    rev.setEncryptedApiKey(new SecretEncryptionService(KEY, 1).encrypt("sk-live-test"));
    rev.setApiKeyEncryptionVersion(1);

    ChatModel model = factory(false).create(rev);
    assertThat(model).isNotNull();
  }

  @Test
  @DisplayName("should reject unsafe base url when creating model")
  void shouldRejectUnsafeBaseUrl() {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setBaseUrl("https://169.254.169.254/v1");
    rev.setModelName("deepseek-chat");
    rev.setEncryptedApiKey(new SecretEncryptionService(KEY, 1).encrypt("sk-live-test"));
    rev.setApiKeyEncryptionVersion(1);

    assertThatThrownBy(() -> factory(false).create(rev))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
