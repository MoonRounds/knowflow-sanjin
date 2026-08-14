// 受控 Markdown 渲染：markdown-it 关闭 raw HTML，产出可安全插入的 HTML 片段。
// 代码块经 highlight.js 高亮并附加头部（语言徽标 + 复制按钮）；````text````/````flow```` 流程图渲染成分步列表。
// 回答正文中的引用标记 [S1]（G32）：传入 sources 后渲染为可交互 span，悬停/点击由 ChatMessageItem 事件委托处理。
import MarkdownIt from 'markdown-it'
import type { Token } from 'markdown-it'
import hljs from 'highlight.js/lib/common'
import { escapeHtml, isFlowBlock, renderCodeBlock, renderFlow } from './markdownBlocks'
import type { RetrievedSource } from '../api/types/conversation'

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

const citePattern = /\[S(\d+)\]/g

/** 替换文本 token 中的 [Sx]（仅本次来源集合内编号）；集合外编号保持纯文本。 */
function renderCiteText(text: string, sourceCount: number): string {
  const escaped = escapeHtml(text)
  return escaped.replace(citePattern, (match, num: string) => {
    const index = Number(num)
    if (index < 1 || index > sourceCount) {
      return match
    }
    // 不设 role/tabindex：避免与来源面板按钮等 [role=button] 在测试与可访问性上混淆，交互走事件委托
    return `<span class="kf-cite" data-source-index="${index}">[S${index}]</span>`
  })
}

const defaultTextRule = md.renderer.rules.text

export function renderMarkdown(source: string, sources?: RetrievedSource[] | null): string {
  const sourceCount = sources?.length ?? 0
  currentSourceCount = sourceCount
  md.renderer.rules.text = sourceCount > 0 ? renderCiteTextToken : defaultTextRule
  try {
    return md.render(source)
  } finally {
    md.renderer.rules.text = defaultTextRule
  }
}

function renderCiteTextToken(tokens: Token[], idx: number): string {
  return renderCiteText(tokens[idx].content, currentSourceCount)
}

/** renderMarkdown 期间的来源集合大小（text renderer 无闭包环境，用模块级临时值）。 */
let currentSourceCount = 0

/** 复用 markdown-it 的 HTML 转义（与受控渲染同一依赖，避免手写实体边界）。 */
export { escapeHtml }
