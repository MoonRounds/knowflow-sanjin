package knowflow.sanjin.modules.embeddingconfig.assembler;

import knowflow.sanjin.modules.embeddingconfig.entity.EmbeddingConfig;
import knowflow.sanjin.modules.embeddingconfig.vo.EmbeddingConfigResponse;

/** EmbeddingConfig Entity → VO 显式转换：API Key 只暴露掩码，不暴露明文或密文。 */
public final class EmbeddingConfigAssembler {

  private EmbeddingConfigAssembler() {}

  public static EmbeddingConfigResponse toResponse(EmbeddingConfig row) {
    EmbeddingConfigResponse r = new EmbeddingConfigResponse();
    r.setConfigured(true);
    r.setBaseUrl(row.getBaseUrl());
    r.setModelName(row.getModelName());
    r.setApiKeyMasked(row.getApiKeyMasked());
    r.setDimension(row.getDimension());
    r.setUpdatedAt(row.getUpdatedAt());
    return r;
  }

  /** 未保存配置时，回显 yml 引导默认值（configured=false）。 */
  public static EmbeddingConfigResponse toBootstrapResponse(
      String baseUrl, String modelName, String apiKeyMasked, Integer dimension) {
    EmbeddingConfigResponse r = new EmbeddingConfigResponse();
    r.setConfigured(false);
    r.setBaseUrl(baseUrl);
    r.setModelName(modelName);
    r.setApiKeyMasked(apiKeyMasked);
    r.setDimension(dimension);
    r.setUpdatedAt(null);
    return r;
  }
}
