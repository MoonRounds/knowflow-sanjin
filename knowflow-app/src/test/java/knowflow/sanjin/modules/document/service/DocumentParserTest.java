package knowflow.sanjin.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** DocumentParser：Markdown H1/无 H1 title、代码块/列表/链接文本提取、HTML 忽略、TXT 规范化。 */
class DocumentParserTest {

  private final DocumentParser parser = new DocumentParser();

  @Test
  void markdownUsesFirstH1AsTitle() throws Exception {
    String md = "# 我的标题\n\n正文内容\n\n## 小节\n\n更多内容";
    DocumentParser.ParsedDocument parsed =
        parser.parse(new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "a.md", true);
    assertThat(parsed.title()).isEqualTo("我的标题");
    assertThat(parsed.content()).contains("# 我的标题").contains("## 小节").contains("正文内容");
  }

  @Test
  void markdownWithoutH1UsesFilename() throws Exception {
    String md = "## 只有二级标题\n\n内容";
    DocumentParser.ParsedDocument parsed =
        parser.parse(
            new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "notes.md", true);
    assertThat(parsed.title()).isEqualTo("notes");
    assertThat(parsed.content()).contains("## 只有二级标题");
  }

  @Test
  void linkKeepsTextNotUrl() throws Exception {
    String md = "参考 [官方文档](https://example.com/docs) 内容";
    DocumentParser.ParsedDocument parsed =
        parser.parse(new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "a.md", true);
    assertThat(parsed.content()).contains("官方文档").doesNotContain("example.com");
  }

  @Test
  void codeBlockAndListPreserved() throws Exception {
    String md = "## 示例\n\n- 列表项一\n- 列表项二\n\n```java\nSystem.out.println(1);\n```\n";
    DocumentParser.ParsedDocument parsed =
        parser.parse(new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "a.md", true);
    assertThat(parsed.content()).contains("- 列表项一").contains("System.out.println");
  }

  @Test
  void markdownHeadingWithInlineCodeKeepsCodeInTitle() throws Exception {
    String md = "# 查看 `main()` 方法\n\n正文";
    DocumentParser.ParsedDocument parsed =
        parser.parse(new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "a.md", true);
    assertThat(parsed.title()).isEqualTo("查看 main() 方法");
  }

  @Test
  void htmlScriptIgnored() throws Exception {
    String md = "# T\n\n<script>alert(1)</script>\n\n<div>raw</div>";
    DocumentParser.ParsedDocument parsed =
        parser.parse(new ByteArrayInputStream(md.getBytes(StandardCharsets.UTF_8)), "a.md", true);
    assertThat(parsed.content()).doesNotContain("<script>").doesNotContain("alert(1)");
  }

  @Test
  void txtNormalizesCrlf() throws Exception {
    String raw = "line1\r\nline2\rline3\n";
    DocumentParser.ParsedDocument parsed =
        parser.parse(
            new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), "a.txt", false);
    assertThat(parsed.content()).isEqualTo("line1\nline2\nline3\n");
    assertThat(parsed.title()).isEqualTo("a");
  }

  @Test
  void txtWithBomStripsBom() throws Exception {
    byte[] raw = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
    DocumentParser.ParsedDocument parsed =
        parser.parse(new ByteArrayInputStream(raw), "a.txt", false);
    assertThat(parsed.content()).isEqualTo("hi");
  }
}
