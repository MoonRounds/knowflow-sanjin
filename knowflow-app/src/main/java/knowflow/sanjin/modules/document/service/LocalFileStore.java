package knowflow.sanjin.modules.document.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import knowflow.sanjin.modules.document.config.DocumentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地原文件磁盘操作的低层封装：负责正式文件删除与孤儿回收。
 *
 * <p>删除为「先删文件后软删记录」的补偿边界；若文件删除失败但记录已软删，不影响业务正确性（下载会因文件缺失 返回可定位错误），由孤儿回收兜底。 清理临时文件对不存在/已删除路径幂等。
 */
@Component
public class LocalFileStore {

  private static final Logger log = LoggerFactory.getLogger(LocalFileStore.class);

  private final DocumentProperties properties;

  public LocalFileStore(DocumentProperties properties) {
    this.properties = properties;
  }

  /** 删除正式存储中的原文件；不存在视为成功。 */
  public void deleteStored(String storageKey) {
    try {
      Files.deleteIfExists(resolve(storageKey));
    } catch (IOException e) {
      log.warn("删除原文件 {} 失败", storageKey, e);
    }
  }

  /** 判断正式文件是否缺失。 */
  public boolean missing(String storageKey) {
    try {
      return !Files.isRegularFile(resolve(storageKey));
    } catch (IOException e) {
      return true;
    }
  }

  /** 清理临时文件（无论成功或失败路径都调用）。 */
  public void deleteTempQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.debug("清理临时文件 {} 失败", path, e);
    }
  }

  private Path resolve(String storageKey) throws IOException {
    if (storageKey == null
        || storageKey.isBlank()
        || storageKey.contains("/")
        || storageKey.contains("\\")
        || storageKey.contains("..")) {
      throw new IOException("Illegal storage key");
    }
    return properties.storageRootPath().resolve(storageKey);
  }
}
