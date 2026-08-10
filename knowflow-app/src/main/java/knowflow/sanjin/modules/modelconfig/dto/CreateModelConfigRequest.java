package knowflow.sanjin.modules.modelconfig.dto;

import jakarta.validation.constraints.*;

/** 创建 ModelConfig 请求：基础参数 + API Key；保存时 Key 加密、接口只回显掩码。 */
public class CreateModelConfigRequest {

  @NotBlank(message = "{modelconfig.displayName.required}")
  @Size(max = 200, message = "{modelconfig.displayName.max}")
  private String displayName;

  @NotBlank(message = "{modelconfig.providerName.required}")
  @Size(max = 100, message = "{modelconfig.providerName.max}")
  private String providerName;

  @NotBlank(message = "{modelconfig.baseUrl.required}")
  @Size(max = 500, message = "{modelconfig.baseUrl.max}")
  private String baseUrl;

  @NotBlank(message = "{modelconfig.modelName.required}")
  @Size(max = 200, message = "{modelconfig.modelName.max}")
  private String modelName;

  @DecimalMin(value = "0.0", message = "{modelconfig.temperature.min}")
  @DecimalMax(value = "2.0", message = "{modelconfig.temperature.max}")
  private Double temperature;

  @Min(value = 1, message = "{modelconfig.maxOutputTokens.min}")
  @Max(value = 1000000, message = "{modelconfig.maxOutputTokens.max}")
  private Integer maxOutputTokens;

  @NotBlank(message = "{modelconfig.apiKey.required}")
  private String apiKey;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

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

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(Double temperature) {
    this.temperature = temperature;
  }

  public Integer getMaxOutputTokens() {
    return maxOutputTokens;
  }

  public void setMaxOutputTokens(Integer maxOutputTokens) {
    this.maxOutputTokens = maxOutputTokens;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }
}
