package knowflow.sanjin.modules.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import knowflow.sanjin.common.config.ChunkingProperties;

/**
 * 轻量结构感知文本 Chunk 策略（无 Markdown 解析库，留给 Phase 08）。
 *
 * <p>按 {@code #}/{@code ##}/{@code ###} 标题划分区块，维护 heading path；每个区块再按段落聚合，超过 {@code targetChars}
 * 的段落按字符拆分并保留少量 overlap。返回 (chunkText, headingPath) 对。
 */
public class TextChunker {

  private final int targetChars;
  private final int overlapChars;
  private final int maxChunks;

  public TextChunker(ChunkingProperties properties) {
    this.targetChars = properties.getTargetChars();
    this.overlapChars = properties.getOverlapChars();
    this.maxChunks = properties.getMaxChunks();
  }

  public List<Chunk> chunk(String title, String content) {
    List<Section> sections = splitByHeadings(content);
    List<Chunk> chunks = new ArrayList<>();
    for (Section section : sections) {
      for (String piece : splitLongContent(section.body())) {
        if (chunks.size() >= maxChunks) {
          return chunks;
        }
        String headingPath = buildHeadingPath(title, section.headingPath());
        if (piece.isBlank()) {
          continue;
        }
        chunks.add(new Chunk(piece, headingPath));
      }
    }
    return chunks;
  }

  /** 返回 (body, headingPath 数组) 的区块列表；正文前面的无标题内容作为独立区块。 */
  private List<Section> splitByHeadings(String content) {
    List<Section> sections = new ArrayList<>();
    List<String> headingStack = new ArrayList<>();
    StringBuilder currentBody = new StringBuilder();
    String[] lines = content.split("\n");

    for (String line : lines) {
      String heading = parseHeading(line);
      if (heading != null) {
        flush(sections, currentBody, headingStack);
        currentBody = new StringBuilder();
        // 更新 heading stack 到该层级
        int level = headingLevel(line);
        while (headingStack.size() >= level) {
          headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(headingText(line));
      } else {
        currentBody.append(line).append('\n');
      }
    }
    flush(sections, currentBody, headingStack);
    if (sections.isEmpty()) {
      sections.add(new Section("", List.of()));
    }
    return sections;
  }

  private void flush(List<Section> sections, StringBuilder body, List<String> headingPath) {
    String trimmed = body.toString().trim();
    if (!trimmed.isEmpty()) {
      sections.add(new Section(trimmed, List.copyOf(headingPath)));
    }
  }

  /** 解析标题返回文本，非标题行返回 null；只识别 # / ## / ###。 */
  private static String parseHeading(String line) {
    if (line.startsWith("### ")) {
      return line.substring(4);
    }
    if (line.startsWith("## ")) {
      return line.substring(3);
    }
    if (line.startsWith("# ")) {
      return line.substring(2);
    }
    return null;
  }

  private static int headingLevel(String line) {
    if (line.startsWith("### ")) {
      return 3;
    }
    if (line.startsWith("## ")) {
      return 2;
    }
    return 1;
  }

  private static String headingText(String line) {
    return line.replaceFirst("^#+\\s+", "").trim();
  }

  private List<String> splitLongContent(String body) {
    List<String> result = new ArrayList<>();
    for (String paragraph : body.split("\n\\s*\n")) {
      String p = paragraph.trim();
      if (p.isEmpty()) {
        continue;
      }
      while (p.length() > targetChars) {
        int cut = findCutPoint(p, targetChars);
        result.add(p.substring(0, cut).trim());
        p = p.substring(Math.max(0, cut - overlapChars)).trim();
      }
      if (!p.isEmpty()) {
        result.add(p);
      }
    }
    return result;
  }

  /** 在目标长度附近的句子/空格处断开，找不到则硬切。 */
  private static int findCutPoint(String text, int maxLen) {
    int cut = maxLen;
    int lastSentence = text.lastIndexOf(". ", maxLen);
    if (lastSentence > maxLen / 2) {
      return lastSentence + 1;
    }
    int lastSpace = text.lastIndexOf(' ', maxLen);
    if (lastSpace > maxLen / 2) {
      return lastSpace;
    }
    return cut;
  }

  private static String buildHeadingPath(String title, List<String> headingPath) {
    if (headingPath == null || headingPath.isEmpty()) {
      return title;
    }
    StringBuilder sb = new StringBuilder(title);
    for (String h : headingPath) {
      sb.append(" > ").append(h);
    }
    return sb.toString();
  }

  /** 一个 Chunk：正文 + 用于 Embedding 输入的 heading path。 */
  public record Chunk(String text, String headingPath) {}

  private record Section(String body, List<String> headingPath) {}
}
