package knowflow.sanjin.modules.document.service;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Text;

/**
 * 遍历 commonmark AST，提取可检索的规范正文（DECISIONS §14）。
 *
 * <p>规则：标题输出 {@code # 文本} 并换行；列表项前加 {@code - }；链接只保留链接文字；行内 code 保留原文；代码块整体保留在 {@code ```}
 * 围栏内。HTML/脚本（RawText）与图片 URL 一律不输出，防止未净化内容进入正文。
 */
class MarkdownTextExtractor extends AbstractVisitor {

  private final StringBuilder body = new StringBuilder();
  private String firstH1;
  private boolean atLineStart = true;

  String extract(Node root) {
    root.accept(this);
    return body.toString();
  }

  String getFirstH1() {
    return firstH1;
  }

  private void appendInline(String s) {
    body.append(s);
    atLineStart = false;
  }

  private void ensureNewline() {
    if (!atLineStart) {
      body.append('\n');
      atLineStart = true;
    }
  }

  private void appendLine(String s) {
    ensureNewline();
    body.append(s);
    body.append('\n');
    atLineStart = true;
  }

  @Override
  public void visit(Heading heading) {
    String text = plainText(heading);
    if (heading.getLevel() == 1 && firstH1 == null) {
      firstH1 = text;
    }
    appendLine("#".repeat(heading.getLevel()) + " " + text);
    // 不访问子节点：标题文本已由 plainText 提取，避免重复输出
  }

  @Override
  public void visit(Text text) {
    appendInline(text.getLiteral());
  }

  @Override
  public void visit(Link link) {
    // 只保留链接文字，不输出 URL
    visitChildren(link);
  }

  @Override
  public void visit(Code code) {
    appendInline(code.getLiteral());
  }

  @Override
  public void visit(ListItem listItem) {
    ensureNewline();
    body.append("- ");
    atLineStart = false;
    visitChildren(listItem);
    ensureNewline();
  }

  @Override
  public void visit(OrderedList list) {
    visitChildren(list);
    ensureNewline();
  }

  @Override
  public void visit(BulletList list) {
    visitChildren(list);
    ensureNewline();
  }

  @Override
  public void visit(FencedCodeBlock block) {
    appendLine("```");
    String literal = block.getLiteral();
    if (literal != null) {
      body.append(literal);
      if (!literal.endsWith("\n")) {
        body.append('\n');
      }
    }
    appendLine("```");
  }

  @Override
  public void visit(IndentedCodeBlock block) {
    appendLine("```");
    String literal = block.getLiteral();
    if (literal != null) {
      body.append(literal);
      if (!literal.endsWith("\n")) {
        body.append('\n');
      }
    }
    appendLine("```");
  }

  /** 忽略行内 HTML/脚本文本，防止未经净化的内容进入正文。 */
  @Override
  public void visit(org.commonmark.node.HtmlInline htmlInline) {
    // 不输出
  }

  /** 忽略 HTML 块，防止未经净化的内容进入正文。 */
  @Override
  public void visit(org.commonmark.node.HtmlBlock htmlBlock) {
    // 不输出
  }

  /** 忽略图片（不输出 URL/alt）。 */
  @Override
  public void visit(org.commonmark.node.Image image) {
    // 不输出
  }

  private String plainText(Node node) {
    StringBuilder sb = new StringBuilder();
    node.accept(
        new AbstractVisitor() {
          @Override
          public void visit(Text text) {
            sb.append(text.getLiteral());
          }

          @Override
          public void visit(Link link) {
            visitChildren(link);
          }
        });
    return sb.toString().trim();
  }
}
