package knowflow.sanjin.modules.modelconfig.assembler;

import java.util.List;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.vo.ModelConfigResponse;
import knowflow.sanjin.modules.modelconfig.vo.ModelConfigRevisionResponse;
import knowflow.sanjin.modules.modelconfig.vo.OwnerAiSettingsResponse;

/** ModelConfig 相关 Entity → VO 的显式转换：API Key 只暴露掩码，不暴露明文或密文。 */
public final class ModelConfigAssembler {

  private ModelConfigAssembler() {}

  public static ModelConfigResponse toResponse(ModelConfig config, ModelConfigRevision current) {
    ModelConfigResponse r = new ModelConfigResponse();
    r.setId(config.getId().toString());
    r.setDisplayName(config.getDisplayName());
    r.setProviderName(config.getProviderName());
    r.setEnabled(config.getEnabled() != null && config.getEnabled());
    r.setCurrentRevisionId(
        config.getCurrentRevisionId() != null ? config.getCurrentRevisionId().toString() : null);
    if (current != null) {
      r.setCurrentRevision(toRevisionSummary(current));
    }
    r.setCreatedAt(config.getCreatedAt());
    r.setUpdatedAt(config.getUpdatedAt());
    return r;
  }

  public static List<ModelConfigResponse> toResponseList(List<ModelConfig> configs) {
    return configs.stream().map(c -> toResponse(c, null)).toList();
  }

  private static ModelConfigResponse.RevisionSummary toRevisionSummary(ModelConfigRevision rev) {
    ModelConfigResponse.RevisionSummary s = new ModelConfigResponse.RevisionSummary();
    s.setId(rev.getId().toString());
    s.setRevisionNo(rev.getRevisionNo());
    s.setDisplayName(rev.getDisplayName());
    s.setProviderName(rev.getProviderName());
    s.setBaseUrl(rev.getBaseUrl());
    s.setModelName(rev.getModelName());
    s.setTemperature(rev.getTemperature());
    s.setMaxOutputTokens(rev.getMaxOutputTokens());
    s.setApiKeyMasked(rev.getApiKeyMasked());
    s.setCreatedAt(rev.getCreatedAt());
    return s;
  }

  public static ModelConfigRevisionResponse toRevisionResponse(ModelConfigRevision rev) {
    ModelConfigRevisionResponse r = new ModelConfigRevisionResponse();
    r.setId(rev.getId().toString());
    r.setModelConfigId(rev.getModelConfigId().toString());
    r.setRevisionNo(rev.getRevisionNo());
    r.setProviderType(rev.getProviderType());
    r.setDisplayName(rev.getDisplayName());
    r.setProviderName(rev.getProviderName());
    r.setBaseUrl(rev.getBaseUrl());
    r.setModelName(rev.getModelName());
    r.setTemperature(rev.getTemperature());
    r.setMaxOutputTokens(rev.getMaxOutputTokens());
    r.setApiKeyMasked(rev.getApiKeyMasked());
    r.setApiKeyEncryptionVersion(
        rev.getApiKeyEncryptionVersion() != null ? rev.getApiKeyEncryptionVersion() : 0);
    r.setCreatedAt(rev.getCreatedAt());
    return r;
  }

  public static List<ModelConfigRevisionResponse> toRevisionResponseList(
      List<ModelConfigRevision> list) {
    return list.stream().map(ModelConfigAssembler::toRevisionResponse).toList();
  }

  public static OwnerAiSettingsResponse toSettingsResponse(OwnerAiSettings settings) {
    OwnerAiSettingsResponse r = new OwnerAiSettingsResponse();
    r.setDefaultChatModelConfigId(
        settings.getDefaultChatModelConfigId() != null
            ? settings.getDefaultChatModelConfigId().toString()
            : null);
    r.setUtilityModelConfigId(
        settings.getUtilityModelConfigId() != null
            ? settings.getUtilityModelConfigId().toString()
            : null);
    r.setUpdatedAt(settings.getUpdatedAt());
    return r;
  }
}
