package knowflow.sanjin.modules.embeddingconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 向量化能力测试结果：真实调用 embedding API，成功时返回探测到的维度。 */
public class EmbeddingVectorizeTestResult {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean success;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String message;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String modelName;

  private Integer dimension;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant testedAt;

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public Integer getDimension() {
    return dimension;
  }

  public void setDimension(Integer dimension) {
    this.dimension = dimension;
  }

  public Instant getTestedAt() {
    return testedAt;
  }

  public void setTestedAt(Instant testedAt) {
    this.testedAt = testedAt;
  }
}
