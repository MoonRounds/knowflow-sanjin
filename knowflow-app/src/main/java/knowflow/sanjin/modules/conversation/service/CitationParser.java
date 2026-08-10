package knowflow.sanjin.modules.conversation.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;

/**
 * 从回答全文解析 {@code [Sx]} 引用，并把 cited 标记落到本次检索集合内的 source 上。
 *
 * <p>只接受本次检索集合内存在的编号；不存在的编号（如 {@code [S99]}）被忽略，不伪造来源。同一编号重复引用视为已标记。
 */
public final class CitationParser {

  private static final Pattern SX = Pattern.compile("\\[S(\\d+)\\]");

  private CitationParser() {}

  /** 返回解析后已设置 cited 的 sources（仅本次集合内的有效引用）。 */
  public static List<RetrievedSource> markCited(List<RetrievedSource> sources, String content) {
    if (sources == null || sources.isEmpty() || content == null || content.isBlank()) {
      return sources;
    }
    Set<Integer> citedIndexes = new HashSet<>();
    Matcher m = SX.matcher(content);
    while (m.find()) {
      int n;
      try {
        n = Integer.parseInt(m.group(1));
      } catch (NumberFormatException e) {
        continue;
      }
      if (n >= 1 && n <= sources.size()) {
        citedIndexes.add(n);
      }
    }
    for (int i = 0; i < sources.size(); i++) {
      if (citedIndexes.contains(i + 1)) {
        sources.get(i).setCited(true);
      }
    }
    return sources;
  }
}
