package knowflow.sanjin.common.util;

/**
 * 可观测性日志工具：统一的中文 key=value 摘要格式与内容截断。
 *
 * <p>摘要日志走 INFO，明细日志走 DEBUG（由 logback 级别开关，默认关闭）。明细内容一律截断，不把完整知识正文/Prompt 写入日志（CLAUDE.md 安全红线）。
 */
public final class ObsLog {

  /** 明细内容默认截断长度（字符）。 */
  public static final int DEFAULT_SNIPPET_CHARS = 200;

  private ObsLog() {}

  /** 截断文本到前 maxChars 个字符；超过时用省略号结尾。null 视为空串。 */
  public static String truncate(String text, int maxChars) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    if (text.length() <= maxChars) {
      return text;
    }
    return text.substring(0, maxChars) + "…";
  }

  /** 格式化耗时（纳秒 → 毫秒），带单位。 */
  public static String formatMs(long nanos) {
    return nanos / 1_000_000 + "ms";
  }

  /** 格式化从 startNanos 至今的耗时（纳秒 -> 毫秒），带单位。 */
  public static String elapsedMs(long startNanos) {
    return formatMs(System.nanoTime() - startNanos);
  }
}
