package knowflow.sanjin.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** MIME 检测与文本校验：二进制伪装、非法 UTF-8、空文件、BOM、CRLF 规范化。 */
class MimeDetectionServiceTest {

  private final MimeDetectionService service = new MimeDetectionService();

  @Test
  void acceptsPlainMarkdown() throws Exception {
    String mime =
        service.detectAndValidate(
            new ByteArrayInputStream("# Title\n\n正文内容".getBytes(StandardCharsets.UTF_8)));
    assertThat(mime).isEqualTo(MimeDetectionService.DETECTED_TEXT_MIME);
  }

  @Test
  void rejectsEmptyFile() {
    assertThatThrownBy(() -> service.detectAndValidate(new ByteArrayInputStream(new byte[0])))
        .isInstanceOf(MimeDetectionService.UnsupportedContentException.class);
  }

  @Test
  void rejectsBinaryWithNul() {
    byte[] payload = new byte[] {'a', 'b', 0x00, 'c', 'd'};
    assertThatThrownBy(() -> service.detectAndValidate(new ByteArrayInputStream(payload)))
        .isInstanceOf(MimeDetectionService.UnsupportedContentException.class);
  }

  @Test
  void rejectsBinaryJpegMagic() {
    // JPEG magic FF D8 FF + binary
    byte[] payload = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x01};
    assertThatThrownBy(() -> service.detectAndValidate(new ByteArrayInputStream(payload)))
        .isInstanceOf(MimeDetectionService.UnsupportedContentException.class);
  }

  @Test
  void rejectsInvalidUtf8() {
    // 非法 UTF-8 多字节序列（孤立 continuation）
    byte[] payload = new byte[] {'a', (byte) 0x80, 'b'};
    assertThatThrownBy(() -> service.detectAndValidate(new ByteArrayInputStream(payload)))
        .isInstanceOf(MimeDetectionService.UnsupportedContentException.class);
  }

  @Test
  void bomOffsetDetectsUtf8Bom() {
    byte[] withBom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
    assertThat(MimeDetectionService.bomOffset(withBom)).isEqualTo(3);
    byte[] noBom = "hi".getBytes(StandardCharsets.UTF_8);
    assertThat(MimeDetectionService.bomOffset(noBom)).isZero();
  }

  @Test
  void normalizeUtf8StripsBomAndNormalizesCrlf() {
    byte[] raw =
        new byte[] {
          (byte) 0xEF,
          (byte) 0xBB,
          (byte) 0xBF,
          'l',
          'i',
          'n',
          'e',
          '1',
          '\r',
          '\n',
          'l',
          'i',
          'n',
          'e',
          '2',
          '\r'
        };
    assertThat(MimeDetectionService.normalizeUtf8(raw)).isEqualTo("line1\nline2\n");
  }

  @Test
  void tempFileRoundTrip(@TempDir Path dir) throws Exception {
    Path f = dir.resolve("test.txt");
    Files.writeString(f, "hello");
    byte[] bytes = Files.readAllBytes(f);
    assertThat(MimeDetectionService.isPlainUtf8(bytes)).isTrue();
  }
}
