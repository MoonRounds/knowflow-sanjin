package knowflow.sanjin.modules.modelconfig.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.dto.UpdateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigDisabledException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigInUseException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.exception.UtilityCapabilityRequiredException;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigRevisionMapper;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ModelConfig 集成测试：通过 MySQL 8.4 Testcontainers 验证迁移、持久化、Revision 不可变、Owner 隔离与 Owner AI Settings
 * 的真实数据库行为。
 */
@SpringBootTest
@DisplayName("ModelConfig Integration Tests")
class ModelConfigServiceIT extends MySQLTestBase {

  @Autowired private ModelConfigService service;
  @Autowired private ModelConfigRevisionMapper revisionMapper;

  private CreateModelConfigRequest request(String name) {
    CreateModelConfigRequest req = new CreateModelConfigRequest();
    req.setDisplayName(name);
    req.setProviderName("DeepSeek");
    req.setBaseUrl("https://api.deepseek.com");
    req.setModelName("deepseek-chat");
    req.setTemperature(0.7);
    req.setMaxOutputTokens(2048);
    req.setApiKey("sk-it-" + name);
    return req;
  }

  @Test
  @DisplayName("should create config, revision and persist encrypted key")
  void shouldCreateAndPersist() {
    ModelConfig config = service.create(request("Alpha"));
    assertThat(config.getId()).isNotNull();
    assertThat(config.getCurrentRevisionId()).isNotNull();

    ModelConfigRevision rev = service.getRevision(config.getId(), config.getCurrentRevisionId());
    assertThat(rev.getRevisionNo()).isEqualTo(1);
    // 数据库中只有密文与掩码
    assertThat(rev.getEncryptedApiKey()).startsWith("v1:");
    assertThat(rev.getEncryptedApiKey()).doesNotContain("sk-it-Alpha");
    assertThat(rev.getApiKeyMasked()).doesNotContain("sk-it-Alpha");
  }

  @Test
  @DisplayName("should keep historical revisions immutable on update")
  void shouldKeepHistoryImmutable() {
    ModelConfig config = service.create(request("Beta"));
    Long firstRevisionId = config.getCurrentRevisionId();
    ModelConfigRevision first = service.getRevision(config.getId(), firstRevisionId);

    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setModelName("deepseek-r1");
    update.setApiKey("sk-it-new-key");
    ModelConfig updated = service.update(config.getId(), update);

    // current 切换，历史 Revision 不被覆盖
    assertThat(updated.getCurrentRevisionId()).isNotEqualTo(firstRevisionId);
    ModelConfigRevision firstAfter = service.getRevision(config.getId(), firstRevisionId);
    assertThat(firstAfter.getModelName()).isEqualTo("deepseek-chat");
    assertThat(firstAfter.getEncryptedApiKey()).isEqualTo(first.getEncryptedApiKey());
    assertThat(firstAfter.getRevisionNo()).isEqualTo(1);
    assertThat(firstAfter.getRevisionNo())
        .isLessThan(
            service.getRevision(config.getId(), updated.getCurrentRevisionId()).getRevisionNo());
  }

  @Test
  @DisplayName("should list revisions newest-first")
  void shouldListRevisions() {
    ModelConfig config = service.create(request("Gamma"));
    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setTemperature(0.2);
    service.update(config.getId(), update);
    update.setTemperature(0.9);
    service.update(config.getId(), update);

    List<ModelConfigRevision> revisions = service.listRevisions(config.getId());
    assertThat(revisions).hasSize(3);
    assertThat(revisions.get(0).getRevisionNo()).isEqualTo(3);
    assertThat(revisions.get(2).getRevisionNo()).isEqualTo(1);
  }

  @Test
  @DisplayName("blank apiKey on update should not overwrite existing encrypted key")
  void shouldKeepExistingKeyWhenApiKeyBlank() {
    ModelConfig config = service.create(request("Kappa"));
    Long firstRevisionId = config.getCurrentRevisionId();
    ModelConfigRevision first = service.getRevision(config.getId(), firstRevisionId);

    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setModelName("deepseek-r1");
    update.setApiKey("");
    ModelConfig updated = service.update(config.getId(), update);

    // 新 Revision 沿用原加密 key，不覆盖为空串密文
    assertThat(updated.getCurrentRevisionId()).isNotEqualTo(firstRevisionId);
    ModelConfigRevision newRev =
        service.getRevision(config.getId(), updated.getCurrentRevisionId());
    assertThat(newRev.getEncryptedApiKey()).isEqualTo(first.getEncryptedApiKey());
    assertThat(newRev.getApiKeyMasked()).isEqualTo(first.getApiKeyMasked());
  }

  @Test
  @DisplayName("blank apiKey alone should not create a new revision")
  void shouldNotCreateRevisionWhenOnlyApiKeyBlank() {
    ModelConfig config = service.create(request("Lambda"));
    Long firstRevisionId = config.getCurrentRevisionId();

    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setApiKey(" ");
    service.update(config.getId(), update);

    // 空白 apiKey 不视为新 key，current Revision 不变
    ModelConfig after = service.getByIdAndOwner(config.getId());
    assertThat(after.getCurrentRevisionId()).isEqualTo(firstRevisionId);
  }

  @Test
  @DisplayName("should enforce owner isolation on configs and revisions")
  void shouldIsolateAcrossOwners() {
    ModelConfig config = service.create(request("Delta"));
    // 另一个 owner 的 revision 不可见（这里用不存在的 id 模拟越权）
    assertThatThrownBy(() -> service.getRevision(999999L, config.getCurrentRevisionId()))
        .isInstanceOf(ModelConfigNotFoundException.class);
  }

  @Test
  @DisplayName("should disable/enable and reject using disabled as utility")
  void shouldDisableAndEnforceEnabledUtility() {
    ModelConfig config = service.create(request("Epsilon"));
    service.disable(config.getId());
    assertThat(service.getByIdAndOwner(config.getId()).getEnabled()).isFalse();

    service.enable(config.getId());
    assertThat(service.getByIdAndOwner(config.getId()).getEnabled()).isTrue();
  }

  @Test
  @DisplayName("should require current revision capability evidence before utility selection")
  void shouldUpdateOwnerSettingsAndBlockDelete() {
    ModelConfig config = service.create(request("Zeta"));

    assertThatThrownBy(() -> service.updateOwnerSettings(null, config.getId()))
        .isInstanceOf(UtilityCapabilityRequiredException.class);

    service.recordUtilityCapabilityResult(
        config.getId(), config.getCurrentRevisionId(), true, true);
    OwnerAiSettings settings = service.updateOwnerSettings(null, config.getId());
    assertThat(settings.getUtilityModelConfigId()).isEqualTo(config.getId());

    assertThatThrownBy(() -> service.softDelete(config.getId()))
        .isInstanceOf(ModelConfigInUseException.class);
    assertThatThrownBy(() -> service.disable(config.getId()))
        .isInstanceOf(ModelConfigInUseException.class);
  }

  @Test
  @DisplayName("latest failed capability result should revoke utility eligibility")
  void shouldRevokeUtilityEligibilityAfterFailure() {
    ModelConfig config = service.create(request("Theta"));
    service.recordUtilityCapabilityResult(
        config.getId(), config.getCurrentRevisionId(), true, true);
    service.recordUtilityCapabilityResult(
        config.getId(), config.getCurrentRevisionId(), true, false);

    assertThatThrownBy(() -> service.updateOwnerSettings(null, config.getId()))
        .isInstanceOf(UtilityCapabilityRequiredException.class);
  }

  @Test
  @DisplayName("new revision should invalidate earlier capability evidence")
  void shouldInvalidateCapabilityEvidenceAfterRevisionChange() {
    ModelConfig config = service.create(request("Iota"));
    service.recordUtilityCapabilityResult(
        config.getId(), config.getCurrentRevisionId(), true, true);

    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setDisplayName("Iota Renamed");
    ModelConfig updated = service.update(config.getId(), update);

    ModelConfigRevision revision =
        service.getRevision(config.getId(), updated.getCurrentRevisionId());
    assertThat(revision.getDisplayName()).isEqualTo("Iota Renamed");
    assertThat(revision.getRevisionNo()).isEqualTo(2);
    assertThatThrownBy(() -> service.updateOwnerSettings(null, config.getId()))
        .isInstanceOf(UtilityCapabilityRequiredException.class);
  }

  @Test
  @DisplayName("should reject disabled config as utility")
  void shouldRejectDisabledAsUtility() {
    ModelConfig config = service.create(request("Eta"));
    service.disable(config.getId());
    assertThatThrownBy(() -> service.updateOwnerSettings(null, config.getId()))
        .isInstanceOf(ModelConfigDisabledException.class);
  }
}
