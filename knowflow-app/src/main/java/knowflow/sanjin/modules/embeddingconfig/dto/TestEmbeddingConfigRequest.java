package knowflow.sanjin.modules.embeddingconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 手动预检请求：用候选配置真实调用一次向量化，返回探测到的维度；不持久化任何内容。 */
public class TestEmbeddingConfigRequest {

  @NotBlank(message = "向量模型 Base URL 不能为空")
  @Size(max = 500, message = "向量模型 Base URL 过长")
  private String baseUrl;

  @NotBlank(message = "向量模型名称不能为空")
  @Size(max = 200, message = "向量模型名称过长")
  private String modelName;

  @NotBlank(message = "测试向量化必须提供 API Key")
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
