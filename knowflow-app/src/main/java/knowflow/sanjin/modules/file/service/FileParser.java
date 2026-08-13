package knowflow.sanjin.modules.file.service;

import java.io.IOException;
import java.io.InputStream;
import knowflow.sanjin.modules.file.exception.FileParseException;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Service;

/**
 * 文档解析器：将原文件规范为 UTF-8 文本。
 *
 * <p>Markdown 使用 commonmark AST 遍历，保留链接文字、标题层级路径、代码块与列表的可检索文本；不执行 HTML/脚本 （commonmark 默认解析器把行内 HTML
 * 视为原始 HTML 节点，本实现直接跳过，不渲染）。TXT 按 UTF-8 读取。
 *
 * <p>title 规则：Markdown 优先取第一个 H1 文本，否则取安全化文件名。
 */
@Service
public class FileParser {

  private final Parser markdownParser;

  public FileParser() {
    this.markdownParser = Parser.builder().build();
  }

  /** 解析文档内容，返回规范正文与建议 title。 */
  public ParsedFile parse(InputStream in, String filename, boolean isMarkdown)
      throws FileParseException {
    String normalized = readNormalized(in);
    if (isMarkdown) {
      return parseMarkdown(normalized, filename);
    }
    return new ParsedFile(normalized, titleFromFilename(filename));
  }

  private String readNormalized(InputStream in) throws FileParseException {
    try {
      byte[] bytes = in.readAllBytes();
      return MimeDetectionService.normalizeUtf8(bytes);
    } catch (IOException e) {
      throw new FileParseException("读取文件失败：" + e.getMessage());
    }
  }

  private ParsedFile parseMarkdown(String markdown, String filename) {
    Node root = markdownParser.parse(markdown);
    MarkdownTextExtractor extractor = new MarkdownTextExtractor();
    String text = extractor.extract(root);
    String title =
        extractor.getFirstH1() != null ? extractor.getFirstH1() : titleFromFilename(filename);
    return new ParsedFile(text, title);
  }

  static String titleFromFilename(String filename) {
    if (filename == null) {
      return "Untitled File";
    }
    String base = filename.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    base = slash >= 0 ? base.substring(slash + 1) : base;
    if (base.isBlank()) {
      return "Untitled File";
    }
    int dot = base.lastIndexOf('.');
    String name = dot > 0 ? base.substring(0, dot) : base;
    return name.isBlank() ? "Untitled File" : name;
  }

  /** 解析结果：规范正文 + 建议 title。 */
  public record ParsedFile(String content, String title) {}
}
