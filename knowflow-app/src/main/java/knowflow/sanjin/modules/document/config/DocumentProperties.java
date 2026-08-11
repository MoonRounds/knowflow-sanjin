package knowflow.sanjin.modules.document.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 8 文档上传配置：本地存储根目录、大小上限与扩展名规则。 */
@ConfigurationProperties(prefix = "knowflow.document")
public class DocumentProperties {

  /** 本地原文件存储根目录（相对启动目录或绝对路径）。 */
  private String storageRoot = "knowflow-data/files";

  /** 单文件大小上限（字节），默认 5 MiB。 */
  private long maxFileBytes = 5 * 1024 * 1024;

  /** 支持的原文件扩展名（不含点）。 */
  private String[] allowedExtensions = {"md", "markdown", "txt"};

  public Path storageRootPath() {
    return Path.of(storageRoot);
  }

  public String getStorageRoot() {
    return storageRoot;
  }

  public void setStorageRoot(String storageRoot) {
    this.storageRoot = storageRoot;
  }

  public long getMaxFileBytes() {
    return maxFileBytes;
  }

  public void setMaxFileBytes(long maxFileBytes) {
    this.maxFileBytes = maxFileBytes;
  }

  public String[] getAllowedExtensions() {
    return allowedExtensions;
  }

  public void setAllowedExtensions(String[] allowedExtensions) {
    this.allowedExtensions = allowedExtensions;
  }
}
