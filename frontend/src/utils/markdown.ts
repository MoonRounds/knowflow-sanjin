// 受控 Markdown 渲染：markdown-it 关闭 raw HTML，产出可安全插入的 HTML 片段。
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

export function renderMarkdown(source: string): string {
  return md.render(source)
}

/** 复用 markdown-it 的 HTML 转义（与受控渲染同一依赖，避免手写实体边界）。 */
export function escapeHtml(source: string): string {
  return md.utils.escapeHtml(source)
}
