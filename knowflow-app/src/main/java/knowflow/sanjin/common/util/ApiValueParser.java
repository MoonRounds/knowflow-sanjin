package knowflow.sanjin.common.util;

import knowflow.sanjin.common.exception.PreconditionRequiredException;

/**
 * API 路径/请求头参数解析：BIGINT 字符串 id 转 long，If-Match 强 ETag 版本解析。
 *
 * <p>解析失败抛 {@link IllegalArgumentException}（校验失败 → 400）；If-Match 缺失抛 {@link
 * PreconditionRequiredException}（前置条件缺失 → 428）。
 */
public final class ApiValueParser {

  private ApiValueParser() {}

  public static Long positiveId(String value, String fieldName) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new IllegalArgumentException(fieldName + " must be a positive integer string");
      }
      return id;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(fieldName + " must be a positive integer string");
    }
  }

  public static int requiredStrongEtagVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new PreconditionRequiredException("If-Match is required for this write operation");
    }
    if (ifMatch.length() < 3
        || ifMatch.charAt(0) != '"'
        || ifMatch.charAt(ifMatch.length() - 1) != '"') {
      throw new IllegalArgumentException("If-Match must be a quoted non-negative rowVersion");
    }
    String value = ifMatch.substring(1, ifMatch.length() - 1);
    try {
      int version = Integer.parseInt(value);
      if (version < 0) {
        throw new IllegalArgumentException("If-Match rowVersion must not be negative");
      }
      return version;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("If-Match must be a quoted non-negative rowVersion");
    }
  }

  public static String strongEtag(int rowVersion) {
    return "\"" + rowVersion + "\"";
  }
}
