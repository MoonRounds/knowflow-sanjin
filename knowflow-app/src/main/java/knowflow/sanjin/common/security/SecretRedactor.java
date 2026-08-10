package knowflow.sanjin.common.security;

import java.util.regex.Pattern;

/** 对日志与响应中的敏感值进行脱敏，避免 API Key 泄漏到日志、错误响应或 OpenAPI 快照。 */
public final class SecretRedactor {

  /** 保持前 4 位与后 4 位，中间以星号替换。短值整体脱敏。 */
  public static String mask(String secret) {
    if (secret == null || secret.isEmpty()) {
      return "";
    }
    if (secret.length() <= 8) {
      return "********";
    }
    int keep = 4;
    return secret.substring(0, keep) + "******" + secret.substring(secret.length() - keep);
  }

  /** 生成持久化掩码：只保留前 4 位 + 星号（用于 UI 回显与 Revision 记录）。 */
  public static String maskForDisplay(String secret) {
    if (secret == null || secret.isEmpty()) {
      return "";
    }
    if (secret.length() <= 4) {
      return "****";
    }
    return secret.substring(0, 4) + "************";
  }

  private static final Pattern API_KEY_PATTERN = Pattern.compile("(sk-[A-Za-z0-9_\\-]{6,})");

  /** 将常见 API Key 形态替换为掩码。 */
  public static String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return API_KEY_PATTERN.matcher(text).replaceAll(m -> mask(m.group(1)));
  }
}
