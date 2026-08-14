package knowflow.sanjin.modules.embeddingconfig.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import knowflow.sanjin.common.config.EmbeddingProperties;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.common.security.SecretRedactor;
import knowflow.sanjin.modules.embeddingconfig.entity.EmbeddingConfig;
import knowflow.sanjin.modules.embeddingconfig.mapper.EmbeddingConfigMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

/** EmbeddingConfigBootstrap 启动引导单测：缺 key 跳过 seed、已有行不覆盖、正常 seed 加密落库。 */
@DisplayName("EmbeddingConfigBootstrap 启动引导")
class EmbeddingConfigBootstrapTest {

  private static final byte[] TEST_KEY =
      Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY=");
  private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

  private EmbeddingConfigMapper mapper;
  private EmbeddingProperties properties;
  private EmbeddingConfigBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    mapper = mock(EmbeddingConfigMapper.class);
    properties = new EmbeddingProperties();
    bootstrap =
        new EmbeddingConfigBootstrap(
            mapper,
            properties,
            new SecretEncryptionService(TEST_KEY, 1),
            new CurrentOwnerProvider());
  }

  @Test
  @DisplayName("base-url 为空时跳过 seed")
  void seedSkipsWhenBaseUrlBlank() {
    properties.setBaseUrl("");
    properties.setApiKey("sk-secret");
    bootstrap.seedFromProperties();
    verify(mapper, never()).insert(ArgumentMatchers.<EmbeddingConfig>any());
  }

  @Test
  @DisplayName("已配置 base-url 但 api-key 为空时跳过 seed，不落库")
  void seedSkipsWhenApiKeyBlank() {
    properties.setBaseUrl(BASE_URL);
    properties.setApiKey("");
    bootstrap.seedFromProperties();
    verify(mapper, never()).insert(ArgumentMatchers.<EmbeddingConfig>any());
  }

  @Test
  @DisplayName("已配置 base-url 但 api-key 为 null 时跳过 seed，不落库")
  void seedSkipsWhenApiKeyNull() {
    properties.setBaseUrl(BASE_URL);
    properties.setApiKey(null);
    bootstrap.seedFromProperties();
    verify(mapper, never()).insert(ArgumentMatchers.<EmbeddingConfig>any());
  }

  @Test
  @DisplayName("已存在配置行时不覆盖 seed")
  void seedSkipsWhenRowExists() {
    properties.setBaseUrl(BASE_URL);
    properties.setApiKey("sk-secret");
    when(mapper.selectById(1L)).thenReturn(new EmbeddingConfig());
    bootstrap.seedFromProperties();
    verify(mapper, never()).insert(ArgumentMatchers.<EmbeddingConfig>any());
  }

  @Test
  @DisplayName("base-url 与 api-key 齐备且无现有行时 seed 一行并加密 key")
  void seedInsertsWhenBaseUrlAndKeyPresent() {
    properties.setBaseUrl(BASE_URL);
    properties.setApiKey("sk-secret");
    when(mapper.selectById(1L)).thenReturn(null);
    bootstrap.seedFromProperties();

    ArgumentCaptor<EmbeddingConfig> captor = ArgumentCaptor.forClass(EmbeddingConfig.class);
    verify(mapper).insert(captor.capture());
    EmbeddingConfig row = captor.getValue();
    assertThat(row.getId()).isEqualTo(1L);
    assertThat(row.getApiKeyMasked()).isEqualTo(SecretRedactor.maskForDisplay("sk-secret"));
    assertThat(new SecretEncryptionService(TEST_KEY, 1).decrypt(row.getEncryptedApiKey()))
        .isEqualTo("sk-secret");
  }
}
