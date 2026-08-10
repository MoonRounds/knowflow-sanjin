package knowflow.sanjin.modules.modelconfig.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.Base64;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.dto.UpdateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigInUseException;
import knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigMapper;
import knowflow.sanjin.modules.modelconfig.mapper.ModelConfigRevisionMapper;
import knowflow.sanjin.modules.modelconfig.mapper.OwnerAiSettingsMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 不依赖 Spring 容器的 ModelConfigService 单元测试，使用 Mockito Mapper。 */
class ModelConfigServiceTest {

  private ModelConfigService service;
  private ModelConfigRevisionMapper revisionMapper;
  private OwnerAiSettingsMapper ownerAiSettingsMapper;
  private ModelConfigMapper modelConfigMapper;
  private SecretEncryptionService encryption;

  private static final byte[] KEY =
      Base64.getDecoder().decode("S25vd0Zsb3ctVGVzdC1NYXN0ZXItS2V5LTAxMjM0NTY=");

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "model-config-test"),
        ModelConfig.class);
    encryption = new SecretEncryptionService(KEY, 1);
    revisionMapper = mock(ModelConfigRevisionMapper.class);
    ownerAiSettingsMapper = mock(OwnerAiSettingsMapper.class);
    modelConfigMapper = mock(ModelConfigMapper.class);
    CurrentOwnerProvider owner = new CurrentOwnerProvider();
    service =
        new ModelConfigService(
            owner,
            encryption,
            new BaseUrlValidator(true),
            modelConfigMapper,
            revisionMapper,
            ownerAiSettingsMapper);
  }

  private CreateModelConfigRequest request(String name) {
    CreateModelConfigRequest req = new CreateModelConfigRequest();
    req.setDisplayName(name);
    req.setProviderName("DeepSeek");
    req.setBaseUrl("http://localhost:9000/v1");
    req.setModelName("deepseek-chat");
    req.setTemperature(0.7);
    req.setMaxOutputTokens(2048);
    req.setApiKey("sk-test-" + name);
    return req;
  }

  private ModelConfigRevision revisionFor(ModelConfig config) {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setId(config.getCurrentRevisionId());
    rev.setModelConfigId(config.getId());
    rev.setOwnerId(config.getOwnerId());
    rev.setRevisionNo(1);
    rev.setBaseUrl("http://localhost:9000/v1");
    rev.setModelName("deepseek-chat");
    rev.setTemperature(0.7);
    rev.setMaxOutputTokens(2048);
    rev.setEncryptedApiKey(encryption.encrypt("sk-test-original"));
    rev.setApiKeyMasked("sk-t************");
    rev.setApiKeyEncryptionVersion(1);
    return rev;
  }

  private ModelConfig createdConfig() {
    when(modelConfigMapper.insert(any(ModelConfig.class)))
        .thenAnswer(
            inv -> {
              ModelConfig c = inv.getArgument(0);
              c.setId(100L);
              return 1;
            });
    when(revisionMapper.insert(any(ModelConfigRevision.class)))
        .thenAnswer(
            inv -> {
              ModelConfigRevision r = inv.getArgument(0);
              r.setId(200L);
              return 1;
            });
    return service.create(request("Alpha"));
  }

  @Test
  @DisplayName("should create config with first revision and switch current")
  void shouldCreateWithRevision() {
    ModelConfig created = createdConfig();
    assertThat(created.getId()).isEqualTo(100L);
    assertThat(created.getCurrentRevisionId()).isEqualTo(200L);
    assertThat(created.getEnabled()).isTrue();
    assertThat(created.getDeleted()).isFalse();
    verify(modelConfigMapper).insert(any(ModelConfig.class));
    verify(revisionMapper).insert(any(ModelConfigRevision.class));
  }

  @Test
  @DisplayName("should not expose plaintext or ciphertext API key")
  void shouldNotExposeSecret() {
    createdConfig();
    ArgumentCaptor<ModelConfigRevision> captor = ArgumentCaptor.forClass(ModelConfigRevision.class);
    verify(revisionMapper).insert(captor.capture());
    ModelConfigRevision rev = captor.getValue();
    assertThat(rev.getEncryptedApiKey()).isNotEqualTo("sk-test-Alpha");
    assertThat(rev.getEncryptedApiKey()).startsWith("v1:");
    assertThat(rev.getApiKeyMasked()).doesNotContain("sk-test-Alpha");
    assertThat(encryption.decrypt(rev.getEncryptedApiKey())).isEqualTo("sk-test-Alpha");
  }

  @Test
  @DisplayName("should create a new immutable revision on parameter update and switch current")
  void shouldCreateNewRevisionOnUpdate() {
    ModelConfig created = createdConfig();
    ModelConfigRevision rev1 = revisionFor(created);
    when(revisionMapper.selectOne(any(Wrapper.class))).thenReturn(rev1);
    when(modelConfigMapper.selectOne(any(Wrapper.class))).thenReturn(created);
    when(modelConfigMapper.update(any(), any(Wrapper.class))).thenReturn(1);

    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setModelName("deepseek-r1");
    ModelConfig updated = service.update(100L, update);

    assertThat(updated.getCurrentRevisionId()).isEqualTo(200L); // new revision id from mock
    verify(revisionMapper, times(2)).insert(any(ModelConfigRevision.class));
  }

  @Test
  @DisplayName("should create a revision when display name changes")
  void shouldCreateRevisionOnDisplayNameUpdate() {
    ModelConfig created = createdConfig();
    ModelConfigRevision rev1 = revisionFor(created);
    when(revisionMapper.selectOne(any(Wrapper.class))).thenReturn(rev1);
    when(modelConfigMapper.selectOne(any(Wrapper.class))).thenReturn(created);
    when(modelConfigMapper.update(any(), any(Wrapper.class))).thenReturn(1);

    UpdateModelConfigRequest update = new UpdateModelConfigRequest();
    update.setDisplayName("Renamed");
    ModelConfig updated = service.update(100L, update);

    assertThat(updated.getDisplayName()).isEqualTo("Renamed");
    verify(revisionMapper, times(2)).insert(any(ModelConfigRevision.class));
  }

  @Test
  @DisplayName("should block deletion of a config referenced as default or utility")
  void shouldBlockDeleteWhenReferenced() {
    ModelConfig created = createdConfig();
    when(modelConfigMapper.selectOne(any(Wrapper.class))).thenReturn(created);
    OwnerAiSettings settings = new OwnerAiSettings();
    settings.setId(1L);
    settings.setOwnerId(1L);
    settings.setDefaultChatModelConfigId(100L);
    when(ownerAiSettingsMapper.selectOne(any(Wrapper.class))).thenReturn(settings);
    when(modelConfigMapper.updateById(any(ModelConfig.class))).thenReturn(1);

    assertThatThrownBy(() -> service.softDelete(100L))
        .isInstanceOf(ModelConfigInUseException.class);
  }

  @Test
  @DisplayName("should throw not found for config of another owner")
  void shouldIsolateAcrossOwners() {
    when(modelConfigMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThatThrownBy(() -> service.getByIdAndOwner(99L))
        .isInstanceOf(ModelConfigNotFoundException.class);
  }
}
