package knowflow.sanjin.modules.modelconfig.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Base64;
import knowflow.sanjin.common.config.ModelClientProperties;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.vo.ConnectionTestResult;
import knowflow.sanjin.modules.modelconfig.vo.UtilityCapabilityTestResult;
import knowflow.sanjin.testinfra.stub.OpenAiCompatibleStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ModelCapabilityService 测试：通过本地 Stub 验证 Test Connection 与 Utility Capability Test 的语义（普通回复 vs 结构化
 * Schema 校验）。
 */
class ModelCapabilityServiceTest {

  private static final byte[] KEY =
      Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY=");

  private OpenAiCompatibleStub stub;
  private ModelConfigService configService;
  private ModelClientFactory factory;
  private ModelCapabilityService capabilityService;

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

    configService = mock(ModelConfigService.class);
    capabilityService = new ModelCapabilityService(configService, factory);
  }

  @AfterEach
  void tearDown() {
    if (stub != null) {
      stub.close();
    }
  }

  private void stubConfig(long configId, String baseUrl) {
    ModelConfig config = new ModelConfig();
    config.setId(configId);
    config.setOwnerId(1L);
    config.setEnabled(true);
    config.setCurrentRevisionId(1L);
    when(configService.getByIdAndOwner(configId)).thenReturn(config);

    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setId(1L);
    rev.setModelConfigId(configId);
    rev.setOwnerId(1L);
    rev.setRevisionNo(1);
    rev.setBaseUrl(baseUrl);
    rev.setModelName("stub-model");
    rev.setEncryptedApiKey(new SecretEncryptionService(KEY, 1).encrypt("sk-stub-key"));
    rev.setApiKeyEncryptionVersion(1);
    when(configService.getRevision(configId, 1L)).thenReturn(rev);
  }

  @Test
  @DisplayName("should report success when stub returns text")
  void shouldTestConnectionOk() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.NORMAL);
    stubConfig(1L, stub.baseUrl());
    ConnectionTestResult result = capabilityService.testConnection(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMessage()).contains("OK");
    assertThat(result.getOutputTokenCount()).isEqualTo(12);
    assertThat(result.getWarnings()).isEmpty();
  }

  @Test
  @DisplayName("should report failure when stub returns 401")
  void shouldTestConnectionUnauthorized() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.UNAUTHORIZED);
    stubConfig(1L, stub.baseUrl());
    ConnectionTestResult result = capabilityService.testConnection(1L);
    assertThat(result.isSuccess()).isFalse();
    // 错误消息不能包含 API Key
    assertThat(result.getMessage()).doesNotContain("sk-stub-key");
  }

  @Test
  @DisplayName("should report failure when stub returns malformed json")
  void shouldTestConnectionMalformed() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.MALFORMED_JSON);
    stubConfig(1L, stub.baseUrl());
    ConnectionTestResult result = capabilityService.testConnection(1L);
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  @DisplayName("should report failure when stub hangs (total timeout)")
  void shouldTestConnectionTimeout() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.TIMEOUT);
    stubConfig(1L, stub.baseUrl());
    long start = System.currentTimeMillis();
    ConnectionTestResult result = capabilityService.testConnection(1L);
    long elapsed = System.currentTimeMillis() - start;
    assertThat(result.isSuccess()).isFalse();
    assertThat(elapsed).isLessThan(30_000L);
    assertThat(result.getMessage()).doesNotContain("sk-stub-key");
  }

  @Test
  @DisplayName("should validate both utility schemas and persist exact revision evidence")
  void shouldTestUtilitySchemas() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.NORMAL);
    stubConfig(1L, stub.baseUrl());

    UtilityCapabilityTestResult result = capabilityService.testUtilityCapability(1L);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.isRouterSchemaValid()).isTrue();
    assertThat(result.isCandidateSchemaValid()).isTrue();
    verify(configService).recordUtilityCapabilityResult(1L, 1L, true, true);
  }

  @Test
  @DisplayName("should persist a failed result so a previous pass is revoked")
  void shouldRecordFailedUtilityResult() {
    stub.setBehavior(OpenAiCompatibleStub.Behavior.MALFORMED_JSON);
    stubConfig(1L, stub.baseUrl());

    UtilityCapabilityTestResult result = capabilityService.testUtilityCapability(1L);

    assertThat(result.isSuccess()).isFalse();
    verify(configService).recordUtilityCapabilityResult(1L, 1L, false, false);
  }
}
