package knowflow.sanjin.modules.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import knowflow.sanjin.modules.document.config.DocumentProperties;
import knowflow.sanjin.modules.document.exception.FileTooLargeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 本地原文件存取：流式写入临时文件并计算 SHA-256，原子移动到按 storage key 命名的正式存储。
 *
 * <p>存储键为随机 UUID（不含用户文件名），路径完全由系统控制，杜绝路径穿越。临时文件统一放在同分区 {@code <root>/tmp}，保证 {@link Files#move}
 * 可原子移动。失败路径由调用方负责清理；文件正式落盘与数据库 提交之间的补偿由 {@link LocalFileStore} 提供。
 */
@Service
public class FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

  private final DocumentProperties properties;

  public FileStorageService(DocumentProperties properties) {
    this.properties = properties;
  }

  /**
   * 将流式输入写入临时文件，返回临时路径与原始字节 SHA-256。
   *
   * <p>写入过程中累计字节数，超过 maxBytes 即中断并清理临时文件后抛 {@link FileTooLargeException}，避免超大文件占满磁盘。
   */
  public StagedFile stage(InputStream in, long maxBytes) throws IOException {
    Path root = properties.storageRootPath();
    Path tmpDir = root.resolve("tmp");
    Files.createDirectories(tmpDir);
    Path tmp = Files.createTempFile(tmpDir, "upload-", ".tmp");
    MessageDigest digest = sha256();
    long total = 0;
    try (var out = Files.newOutputStream(tmp)) {
      byte[] buf = new byte[64 * 1024];
      int read;
      while ((read = in.read(buf)) != -1) {
        total += read;
        if (total > maxBytes) {
          Files.deleteIfExists(tmp);
          throw new FileTooLargeException("文件超过大小上限 " + maxBytes + " 字节");
        }
        out.write(buf, 0, read);
        digest.update(buf, 0, read);
      }
    }
    return new StagedFile(tmp, HexFormat.of().formatHex(digest.digest()));
  }

  /** 将临时文件原子移动到正式存储位置，返回存储键。 */
  public String commit(StagedFile staged, String storageKey) throws IOException {
    Path root = properties.storageRootPath();
    Path target = root.resolve(storageKey);
    Files.createDirectories(target.getParent());
    try {
      Files.move(
          staged.path(),
          target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(staged.path(), target, StandardCopyOption.REPLACE_EXISTING);
    }
    return storageKey;
  }

  /** 读取正式存储原文件（按存储键校验后）为输入流。 */
  public InputStream read(String storageKey) throws IOException {
    Path path = resolveStorageKey(storageKey);
    if (!Files.isRegularFile(path)) {
      throw new IOException("Stored file missing: " + storageKey);
    }
    return Files.newInputStream(path);
  }

  /** 删除正式存储原文件；不存在视为成功（幂等）。 */
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(resolveStorageKey(storageKey));
    } catch (IOException e) {
      log.warn("删除原文件 {} 失败", storageKey, e);
    }
  }

  /** 生成不含用户信息的随机存储键。 */
  public String newStorageKey() {
    return UUID.randomUUID().toString();
  }

  /** 严格按存储键定位：仅接受不带路径分隔符的单一文件名，杜绝路径穿越。 */
  private Path resolveStorageKey(String storageKey) throws IOException {
    if (storageKey == null
        || storageKey.isBlank()
        || storageKey.contains("/")
        || storageKey.contains("\\")
        || storageKey.contains("..")) {
      throw new IOException("Illegal storage key");
    }
    return properties.storageRootPath().resolve(storageKey);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** 暂存结果：临时路径 + 原始字节 SHA-256。 */
  public record StagedFile(Path path, String sha256) {}
}
