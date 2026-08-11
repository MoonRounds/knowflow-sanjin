package knowflow.sanjin.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import knowflow.sanjin.modules.document.config.DocumentProperties;
import knowflow.sanjin.modules.document.exception.FileTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FileStorageService：流式暂存、原子移动、SHA-256 计算与存储键安全。 */
class FileStorageServiceTest {

  @Test
  void stageComputesSha256AndCommitMovesAtomically(@TempDir Path root) throws Exception {
    DocumentProperties props = new DocumentProperties();
    props.setStorageRoot(root.toString());
    FileStorageService storage = new FileStorageService(props);

    String content = "hello 你好";
    FileStorageService.StagedFile staged =
        storage.stage(
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), Long.MAX_VALUE);
    assertThat(staged.sha256())
        .isEqualTo(
            java.util.HexFormat.of()
                .formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(content.getBytes(StandardCharsets.UTF_8))));

    String key = storage.newStorageKey();
    storage.commit(staged, key);
    assertThat(Files.readString(root.resolve(key))).isEqualTo(content);
    // 临时文件已移走
    assertThat(Files.exists(staged.path())).isFalse();
  }

  @Test
  void rejectsStorageKeyTraversal(@TempDir Path root) {
    DocumentProperties props = new DocumentProperties();
    props.setStorageRoot(root.toString());
    FileStorageService storage = new FileStorageService(props);
    assertThatThrowsTraversal(storage, "../secret");
    assertThatThrowsTraversal(storage, "a/b");
    assertThatThrowsTraversal(storage, "a\\b");
    assertThatThrowsTraversal(storage, "..");
  }

  private static void assertThatThrowsTraversal(FileStorageService storage, String key) {
    try {
      storage.read(key);
      throw new AssertionError("should have thrown for key=" + key);
    } catch (java.io.IOException e) {
      assertThat(e.getMessage()).contains("Illegal storage key");
    }
  }

  @Test
  void stageRejectsOverLimitAndDeletesTempFile(@TempDir Path root) throws Exception {
    DocumentProperties props = new DocumentProperties();
    props.setStorageRoot(root.toString());
    FileStorageService storage = new FileStorageService(props);

    // 上限 10 字节，写入 11 字节，写入过程中必须中断并清理临时文件
    byte[] payload = new byte[11];
    java.util.Arrays.fill(payload, (byte) 'a');

    assertThatThrownBy(() -> storage.stage(new ByteArrayInputStream(payload), 10))
        .isInstanceOf(FileTooLargeException.class)
        .hasMessageContaining("10");
    // 临时文件目录为空
    assertThat(Files.list(root.resolve("tmp"))).isEmpty();
  }

  @Test
  void deleteIsIdempotent(@TempDir Path root) throws Exception {
    DocumentProperties props = new DocumentProperties();
    props.setStorageRoot(root.toString());
    FileStorageService storage = new FileStorageService(props);
    storage.delete("nonexistent-key");
    assertThat(Files.list(root).count()).isZero();
  }
}
