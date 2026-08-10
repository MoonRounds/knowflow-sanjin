package knowflow.sanjin.modules.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 普通 Chat 兼容性测试结果。 */
public class ConnectionTestResult {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean success;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String message;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String modelName;

  private Integer outputTokenCount;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant testedAt;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private List<String> warnings;

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

  public Integer getOutputTokenCount() {
    return outputTokenCount;
  }

  public void setOutputTokenCount(Integer outputTokenCount) {
    this.outputTokenCount = outputTokenCount;
  }

  public Instant getTestedAt() {
    return testedAt;
  }

  public void setTestedAt(Instant testedAt) {
    this.testedAt = testedAt;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings;
  }
}
