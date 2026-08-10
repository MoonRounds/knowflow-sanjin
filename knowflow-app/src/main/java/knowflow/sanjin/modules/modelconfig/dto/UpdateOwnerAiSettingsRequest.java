package knowflow.sanjin.modules.modelconfig.dto;

import jakarta.validation.constraints.NotBlank;

/** 设置 Owner 的默认 Chat Model 与 Utility Model。 */
public class UpdateOwnerAiSettingsRequest {

  /** 可为 null 表示清除默认 Chat Model。 */
  private String defaultChatModelConfigId;

  @NotBlank(message = "{owneraisettings.utility.required}")
  private String utilityModelConfigId;

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
}
