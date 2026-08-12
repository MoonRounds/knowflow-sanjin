package knowflow.sanjin.modules.modelconfig.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** Owner 尚未配置 Utility Model，无法执行依赖它的业务（如知识提取）。 */
public class UtilityModelNotConfiguredException extends RuntimeException {

  public UtilityModelNotConfiguredException() {
    super("尚未配置 Utility 模型，请先在模型设置中配置并启用。");
  }

  public String getErrorCode() {
    return ErrorCode.UTILITY_MODEL_NOT_CONFIGURED;
  }
}
