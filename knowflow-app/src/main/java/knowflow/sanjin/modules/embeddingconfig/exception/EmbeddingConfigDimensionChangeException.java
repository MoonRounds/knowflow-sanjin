package knowflow.sanjin.modules.embeddingconfig.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 保存向量模型配置时维度与当前已索引维度不一致，必须先全量重建索引。 */
public class EmbeddingConfigDimensionChangeException extends RuntimeException {

  public EmbeddingConfigDimensionChangeException(String message) {
    super(message);
  }

  public String getErrorCode() {
    return ErrorCode.EMBEDDING_DIMENSION_CHANGE_REQUIRES_REINDEX;
  }
}
