package knowflow.sanjin.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 应用级主密钥配置。密钥只来自环境 Secret，绝不写入 Git 或数据库。 */
@ConfigurationProperties(prefix = "knowflow.security")
public class SecretKeyProperties {

  /** 当前使用的加密版本。 */
  private int encryptionVersion = 1;

  /** Base64 编码的应用级主密钥（AES-256）。 */
  private String masterKey;

  public int getEncryptionVersion() {
    return encryptionVersion;
  }

  public void setEncryptionVersion(int encryptionVersion) {
    this.encryptionVersion = encryptionVersion;
  }

  public String getMasterKey() {
    return masterKey;
  }

  public void setMasterKey(String masterKey) {
    this.masterKey = masterKey;
  }

  public boolean hasMasterKey() {
    return StringUtils.hasText(masterKey);
  }
}
