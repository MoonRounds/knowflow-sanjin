package knowflow.sanjin.modules.embeddingconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 保存系统级向量模型配置请求。服务端保存时重跑真实向量化测试并自动探测维度； apiKey 为空表示沿用现有加密 Key。 */
public class UpdateEmbeddingConfigRequest {

  @NotBlank(message = "向量模型 Base URL 不能为空")
  @Size(max = 500, message = "向量模型 Base URL 过长")
  private String baseUrl;

  @NotBlank(message = "向量模型名称不能为空")
  @Size(max = 200, message = "向量模型名称过长")
  private String modelName;

  @Size(max = 500, message = "API Key 过长")
  private String apiKey;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }
}
