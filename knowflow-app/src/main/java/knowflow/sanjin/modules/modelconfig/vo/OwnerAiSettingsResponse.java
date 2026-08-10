package knowflow.sanjin.modules.modelconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public class OwnerAiSettingsResponse {

  private String defaultChatModelConfigId;
  private String utilityModelConfigId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Instant updatedAt;

  public String getDefaultChatModelConfigId() {
    return defaultChatModelConfigId;
  }

  public void setDefaultChatModelConfigId(String defaultChatModelConfigId) {
    this.defaultChatModelConfigId = defaultChatModelConfigId;
  }

  public String getUtilityModelConfigId() {
    return utilityModelConfigId;
  }

  public void setUtilityModelConfigId(String utilityModelConfigId) {
    this.utilityModelConfigId = utilityModelConfigId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
