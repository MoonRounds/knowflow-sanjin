// 受控 Markdown 渲染：markdown-it 关闭 raw HTML，产出可安全插入的 HTML 片段。
// 代码块经 highlight.js 高亮并附加头部（语言徽标 + 复制按钮）；````text````/````flow```` 流程图渲染成分步列表。
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'
import { escapeHtml, isFlowBlock, renderCodeBlock, renderFlow } from './markdownBlocks'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

/** 真代码语言走 highlight.js；text/plain/空语言直接转义，不做高亮。 */
function highlightCode(lang: string, code: string): { html: string; highlighted: boolean } {
  if (lang === '' || lang === 'text' || lang === 'plain' || lang === 'txt') {
    return { html: escapeHtml(code), highlighted: false }
  }
  try {
    return {
      html: hljs.highlight(code, { language: lang, ignoreIllegals: true }).value,
      highlighted: true,
    }
  } catch {
    // 未知语言：hljs.highlight 抛错，回退为转义纯文本（仍保留语言徽标与复制按钮）
    return { html: escapeHtml(code), highlighted: false }
  }
}

md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const lang = (token.info || '').trim().split(/\s+/)[0] || ''
  const code = token.content ?? ''
  if (isFlowBlock(lang, code)) {
    return renderFlow(code)
  }
  const { html, highlighted } = highlightCode(lang, code)
  return renderCodeBlock(lang, html, highlighted)
}

export function renderMarkdown(source: string): string {
  return md.render(source)
}

/** 复用 markdown-it 的 HTML 转义（与受控渲染同一依赖，避免手写实体边界）。 */
export { escapeHtml }
