package knowflow.sanjin.modules.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Utility Model 结构化输出能力测试结果。 */
public class UtilityCapabilityTestResult {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean success;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean routerSchemaValid;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private boolean candidateSchemaValid;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String message;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant testedAt;

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public boolean isRouterSchemaValid() {
    return routerSchemaValid;
  }

  public void setRouterSchemaValid(boolean routerSchemaValid) {
    this.routerSchemaValid = routerSchemaValid;
  }

  public boolean isCandidateSchemaValid() {
    return candidateSchemaValid;
  }

  public void setCandidateSchemaValid(boolean candidateSchemaValid) {
    this.candidateSchemaValid = candidateSchemaValid;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getTestedAt() {
    return testedAt;
  }

  public void setTestedAt(Instant testedAt) {
    this.testedAt = testedAt;
  }
}
