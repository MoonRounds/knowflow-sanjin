package knowflow.sanjin.modules.file.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Service;

/**
 * 基于 tika-core 的 MIME 与文本有效性检测（DECISIONS §14：使用 tika-core，不引入完整 Parsers）。
 *
 * <p>检测必须读取实际字节，不能信任扩展名或请求 Content-Type。DefaultDetector 结合 magic bytes 与 TextDetector： 二进制/含 NUL
 * 的内容会被判为 octet-stream，从而拒绝「二进制伪装文本」。对所有合法文本统一规范为 {@code text/plain} 作为去重 MIME。
 *
 * <p>tika 的 TextDetector 只做启发式，仍以严格 UTF-8 校验兜底，确保拒绝非法 UTF-8 与空内容。
 */
@Service
public class MimeDetectionService {

  /** 规范去重 MIME：V1 所有文本类型统一，避免同内容以不同扩展名被看作不同文件。 */
  public static final String DETECTED_TEXT_MIME = "text/plain";

  private final Detector detector = new DefaultDetector();

  /**
   * 检测并校验文本内容。
   *
   * @return 规范化的检测 MIME（去重键组成部分）
   * @throws UnsupportedContentException 内容不是可接受的 UTF-8 文本（二进制 / 非法 UTF-8 / 空）
   */
  public String detectAndValidate(InputStream in) throws IOException, UnsupportedContentException {
    Metadata metadata = new Metadata();
    byte[] head = readHead(in, 8192);
    if (head.length == 0) {
      throw new UnsupportedContentException("空文件");
    }
    if (!isPlainUtf8(head)) {
      throw new UnsupportedContentException("内容不是合法 UTF-8 文本（疑似二进制）");
    }
    MediaType detected = detector.detect(new java.io.ByteArrayInputStream(head), metadata);
    String mime = detected.toString();
    boolean binaryLike =
        "application/octet-stream".equals(mime)
            || mime.startsWith("application/")
            || mime.startsWith("image/")
            || mime.startsWith("audio/")
            || mime.startsWith("video/");
    if (binaryLike) {
      throw new UnsupportedContentException("检测到非文本内容：" + mime);
    }
    return DETECTED_TEXT_MIME;
  }

  private static byte[] readHead(InputStream in, int limit) throws IOException {
    BufferedInputStream buf =
        in instanceof BufferedInputStream ? (BufferedInputStream) in : new BufferedInputStream(in);
    byte[] out = new byte[limit];
    int total = 0;
    int read;
    while (total < limit && (read = buf.read(out, total, limit - total)) != -1) {
      total += read;
    }
    return total == limit ? out : java.util.Arrays.copyOf(out, total);
  }

  /** 字节序列必须是合法 UTF-8，且非二进制伪装（含 NUL 或非法多字节序列即拒绝）。 */
  static boolean isPlainUtf8(byte[] bytes) {
    if (bytes.length == 0) {
      return false;
    }
    int i = bomOffset(bytes);
    boolean sawContent = false;
    while (i < bytes.length) {
      byte b = bytes[i];
      if (b >= 0) {
        if (b >= 0x20 || b == 0x09 || b == 0x0A || b == 0x0D) {
          sawContent = true;
        } else if (b == 0x00 || b == 0x1F) {
          return false;
        }
        i++;
      } else {
        int len = utf8SequenceLength(b);
        if (len < 2 || i + len > bytes.length) {
          return false;
        }
        for (int k = 1; k < len; k++) {
          if ((bytes[i + k] & 0xC0) != 0x80) {
            return false;
          }
        }
        sawContent = true;
        i += len;
      }
    }
    return sawContent;
  }

  /** UTF-8 BOM（EF BB BF）字节长度，0 表示无 BOM。 */
  static int bomOffset(byte[] bytes) {
    if (bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xEF
        && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF) {
      return 3;
    }
    return 0;
  }

  /** 移除 BOM 并按 UTF-8 解码，统一换行为 LF。 */
  static String normalizeUtf8(byte[] bytes) {
    int offset = bomOffset(bytes);
    String raw = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    return raw.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static int utf8SequenceLength(byte first) {
    if ((first & 0xE0) == 0xC0) return 2;
    if ((first & 0xF0) == 0xE0) return 3;
    if ((first & 0xF8) == 0xF0) return 4;
    return -1;
  }

  /** 内容不是可接受的 UTF-8 文本（二进制 / 非法 UTF-8 / 空）。 */
  public static class UnsupportedContentException extends Exception {
    public UnsupportedContentException(String message) {
      super(message);
    }
  }
}
