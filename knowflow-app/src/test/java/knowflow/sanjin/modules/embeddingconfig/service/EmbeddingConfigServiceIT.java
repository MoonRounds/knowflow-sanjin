package knowflow.sanjin.modules.embeddingconfig.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.embeddingconfig.dto.UpdateEmbeddingConfigRequest;
import knowflow.sanjin.modules.embeddingconfig.entity.EmbeddingConfig;
import knowflow.sanjin.modules.embeddingconfig.mapper.EmbeddingConfigMapper;
import knowflow.sanjin.modules.embeddingconfig.vo.EmbeddingConfigResponse;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Embedding 系统级向量模型配置保存/读取集成测试（Testcontainers MySQL）。
 *
 * <p>覆盖：首次保存必须显式提供 API Key（拒绝且不落库）、首次保存带 Key 落库后重启可读取、编辑留空沿用现有 Key。 EmbeddingClient/QdrantClient 用
 * mock 避免真实外部调用。
 */
@SpringBootTest
@DisplayName("EmbeddingConfig 保存/读取集成测试")
class EmbeddingConfigServiceIT extends MySQLTestBase {

  private static final String BASE_URL = "http://127.0.0.1:18080/v1";
  private static final String MODEL = "text-embedding-v4";

  @Autowired private EmbeddingConfigService service;
  @Autowired private EmbeddingConfigMapper mapper;
  @Autowired private SecretEncryptionService encryptionService;

  @MockitoBean private EmbeddingClient embeddingClient;
  @MockitoBean private QdrantClient qdrantClient;

  @BeforeEach
  void setUp() {
    when(embeddingClient.embed(anyList(), any())).thenReturn(List.of(new float[8]));
    when(qdrantClient.collectionDimension(anyString())).thenReturn(Optional.empty());
    // 清空单例行，保证每个用例从"未配置"状态开始（不受启动引导/前序用例影响）
    mapper.deleteById(1L);
  }

  @Test
  @DisplayName("首次保存不带 API Key 被拒绝且不落库")
  void firstSaveWithoutKeyRejected() {
    UpdateEmbeddingConfigRequest req = new UpdateEmbeddingConfigRequest();
    req.setBaseUrl(BASE_URL);
    req.setModelName(MODEL);

    assertThatThrownBy(() -> service.save(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须提供 API Key");
    assertThat(mapper.selectById(1L)).isNull();

    req.setApiKey("   ");
    assertThatThrownBy(() -> service.save(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("必须提供 API Key");
    assertThat(mapper.selectById(1L)).isNull();
  }

  @Test
  @DisplayName("首次保存带 Key 落库后，重新读取（模拟重启）配置与掩码一致")
  void firstSaveWithKeyPersistsAcrossRead() {
    UpdateEmbeddingConfigRequest req = new UpdateEmbeddingConfigRequest();
    req.setBaseUrl(BASE_URL);
    req.setModelName(MODEL);
    req.setApiKey("sk-first-123");
    EmbeddingConfigResponse saved = service.save(req);
    assertThat(saved.isConfigured()).isTrue();
    assertThat(saved.getApiKeyMasked()).isNotBlank();

    EmbeddingConfigResponse reloaded = service.getCurrent();
    assertThat(reloaded.isConfigured()).isTrue();
    assertThat(reloaded.getApiKeyMasked()).isEqualTo(saved.getApiKeyMasked());
    assertThat(reloaded.getBaseUrl()).isEqualTo(BASE_URL);

    EmbeddingConfig row = mapper.selectById(1L);
    assertThat(encryptionService.decrypt(row.getEncryptedApiKey())).isEqualTo("sk-first-123");
  }

  @Test
  @DisplayName("编辑未提供新 Key 时沿用现有加密 Key")
  void editWithoutNewKeyPreservesExistingKey() {
    UpdateEmbeddingConfigRequest first = new UpdateEmbeddingConfigRequest();
    first.setBaseUrl(BASE_URL);
    first.setModelName(MODEL);
    first.setApiKey("sk-original-456");
    EmbeddingConfigResponse saved = service.save(first);

    UpdateEmbeddingConfigRequest edit = new UpdateEmbeddingConfigRequest();
    edit.setBaseUrl("http://127.0.0.1:18080/v2");
    edit.setModelName(MODEL);
    edit.setApiKey(null);
    EmbeddingConfigResponse edited = service.save(edit);

    assertThat(edited.getApiKeyMasked()).isEqualTo(saved.getApiKeyMasked());
    assertThat(edited.getBaseUrl()).isEqualTo("http://127.0.0.1:18080/v2");
    EmbeddingConfig row = mapper.selectById(1L);
    assertThat(encryptionService.decrypt(row.getEncryptedApiKey())).isEqualTo("sk-original-456");
    assertThat(row.getApiKeyEncryptionVersion())
        .isEqualTo(encryptionService.getEncryptionVersion());
  }

  @Test
  @DisplayName("编辑并显式提供新 Key 时替换旧 Key 并更新加密版本")
  void editWithNewKeyReplacesExistingKey() {
    UpdateEmbeddingConfigRequest first = new UpdateEmbeddingConfigRequest();
    first.setBaseUrl(BASE_URL);
    first.setModelName(MODEL);
    first.setApiKey("sk-old-789");
    EmbeddingConfigResponse saved = service.save(first);

    UpdateEmbeddingConfigRequest edit = new UpdateEmbeddingConfigRequest();
    edit.setBaseUrl(BASE_URL);
    edit.setModelName(MODEL);
    edit.setApiKey("sk-new-000");
    EmbeddingConfigResponse edited = service.save(edit);

    assertThat(edited.getApiKeyMasked()).isNotEqualTo(saved.getApiKeyMasked());
    EmbeddingConfig row = mapper.selectById(1L);
    assertThat(encryptionService.decrypt(row.getEncryptedApiKey())).isEqualTo("sk-new-000");
    assertThat(row.getApiKeyEncryptionVersion())
        .isEqualTo(encryptionService.getEncryptionVersion());
  }

  @Test
  @DisplayName("存量空 Key 行编辑留空时返回明确错误而非上游认证失败")
  void legacyEmptyKeyRowEditWithoutKeyRejectedClearly() {
    // 模拟旧 bootstrap seed 出的空串密文行（configured=true 但无有效 Key）
    EmbeddingConfig legacy = new EmbeddingConfig();
    legacy.setId(1L);
    legacy.setOwnerId(1L);
    legacy.setBaseUrl(BASE_URL);
    legacy.setModelName(MODEL);
    legacy.setDimension(1024);
    legacy.setEncryptedApiKey(encryptionService.encrypt(""));
    legacy.setApiKeyMasked("");
    legacy.setApiKeyEncryptionVersion(encryptionService.getEncryptionVersion());
    mapper.insert(legacy);

    UpdateEmbeddingConfigRequest edit = new UpdateEmbeddingConfigRequest();
    edit.setBaseUrl(BASE_URL);
    edit.setModelName(MODEL);
    edit.setApiKey(null);
    assertThatThrownBy(() -> service.save(edit))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未保存有效 API Key");
  }
}
