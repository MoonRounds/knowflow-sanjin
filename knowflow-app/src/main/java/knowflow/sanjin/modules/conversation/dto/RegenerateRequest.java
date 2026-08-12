package knowflow.sanjin.modules.conversation.dto;

/** 重新生成请求：modelConfigId 可选，缺省沿用原 attempt 使用的模型。 */
public class RegenerateRequest {

  private String modelConfigId;

  public String getModelConfigId() {
    return modelConfigId;
  }

  public void setModelConfigId(String modelConfigId) {
    this.modelConfigId = modelConfigId;
  }
}
