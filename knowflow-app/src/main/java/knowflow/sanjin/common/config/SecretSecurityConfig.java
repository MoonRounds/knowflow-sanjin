package knowflow.sanjin.common.config;

import java.util.Base64;
import knowflow.sanjin.common.security.BaseUrlValidator;
import knowflow.sanjin.common.security.SecretEncryptionService;
import knowflow.sanjin.modules.conversation.service.GenerationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 应用级 Secret 安全边界。主密钥只来自环境 Secret（KNOWFLOW_SECURITY_MASTER_KEY）。 缺少主密钥时在启动阶段明确失败，绝不生成或提交临时密钥。 */
@Configuration
@EnableConfigurationProperties({
  SecretKeyProperties.class,
  ModelClientProperties.class,
  GenerationProperties.class
})
public class SecretSecurityConfig {

  @Bean
  public SecretEncryptionService secretEncryptionService(SecretKeyProperties properties) {
    if (!properties.hasMasterKey()) {
      throw new IllegalStateException(
          "KNOWFLOW_SECURITY_MASTER_KEY is not configured. "
              + "ModelConfig API Key 加密需要应用级主密钥（Base64 AES-256）。"
              + "请通过环境变量注入，禁止把临时密钥写入仓库。");
    }
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(properties.getMasterKey());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "knowflow.security.master-key must be a valid Base64-encoded AES-256 key", e);
    }
    if (keyBytes.length != 32) {
      throw new IllegalStateException(
          "KNOWFLOW_SECURITY_MASTER_KEY must decode to exactly 32 bytes for AES-256");
    }
    if (properties.getEncryptionVersion() <= 0) {
      throw new IllegalStateException("knowflow.security.encryption-version must be positive");
    }
    return new SecretEncryptionService(keyBytes, properties.getEncryptionVersion());
  }

  @Bean
  public BaseUrlValidator baseUrlValidator(ModelClientProperties properties) {
    return new BaseUrlValidator(properties.isAllowLocalBaseUrl());
  }
}
