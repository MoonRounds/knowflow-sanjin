// 受控 Markdown 渲染测试：确认基本语法被渲染、raw HTML 被禁用（安全边界）、代码块高亮与流程图识别。
// [Sx] 引用标记（G32）：传入 sources 后集合内编号渲染为可交互 span，集合外编号保持纯文本。
import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '../markdown'
import type { RetrievedSource } from '../../api/types/conversation'

const twoSources = [
  { sourceId: 'c1', documentId: '1', documentTitle: 'a' },
  { sourceId: 'c2', documentId: '2', documentTitle: 'b' },
] as RetrievedSource[]

describe('renderMarkdown', () => {
  it('renders headings, bold, inline code and highlighted code blocks', () => {
    const html = renderMarkdown('# 标题\n\n**加粗** 和 `inline`\n\n```js\nconst a = 1\n```')
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<strong>加粗</strong>')
    expect(html).toContain('<code>inline</code>')
    expect(html).toContain('<pre><code class="language-js hljs">')
    expect(html).toContain('kf-codeblock')
  })

  it('adds language badge and copy button to code blocks', () => {
    const html = renderMarkdown('```python\nprint("hi")\n```')
    expect(html).toContain('<span class="kf-lang">python</span>')
    expect(html).toContain('<button type="button" class="kf-copy-btn">复制</button>')
    expect(html).toContain('language-python hljs')
  })

  it('renders text flow diagram as numbered steps, not a code block', () => {
    const html = renderMarkdown('```text\n用户提问\n  ↓\nAgent 理解意图\n  ↓\n系统执行 Skill\n```')
    expect(html).toContain('kf-flow')
    expect(html).toContain('<li>用户提问</li>')
    expect(html).toContain('<li>Agent 理解意图</li>')
    expect(html).toContain('<li>系统执行 Skill</li>')
    expect(html).not.toContain('kf-codeblock')
  })

  it('treats explicit flow language tag as a flow block', () => {
    const html = renderMarkdown('```flow\na\n↓\nb\n```')
    expect(html).toContain('kf-flow')
    expect(html).toContain('<li>a</li>')
  })

  it('keeps text blocks without arrows as code blocks', () => {
    const html = renderMarkdown('```text\n普通文本第一行\n普通文本第二行\n```')
    expect(html).toContain('kf-codeblock')
    expect(html).not.toContain('kf-flow')
  })

  it('keeps real code languages as code blocks even with arrows', () => {
    const html = renderMarkdown('```js\nconst a = 1\nconst b = 2\nconst c = a -> b\n```')
    expect(html).toContain('kf-codeblock')
    expect(html).not.toContain('kf-flow')
  })

  it('escapes raw HTML inside highlighted code blocks', () => {
    const html = renderMarkdown('```html\n<script>alert(1)</script>\n```')
    // hljs 会把转义结果拆进多个 span，因此只断言关键转义字符存在、无原始 <script> 注入
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;')
  })

  it('renders unordered and ordered lists', () => {
    const html = renderMarkdown('- a\n- b\n\n1. x\n2. y')
    expect(html).toContain('<ul>')
    expect(html).toContain('<ol>')
    expect(html).toContain('<li>a</li>')
  })

  it('renders links and tables', () => {
    const html = renderMarkdown('[链接](https://example.com)\n\n| a | b |\n|---|---|\n| 1 | 2 |')
    expect(html).toContain('<a href="https://example.com">链接</a>')
    expect(html).toContain('<table>')
  })

  it('escapes raw HTML instead of injecting it', () => {
    const html = renderMarkdown(
      '正常文本\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>',
    )
    expect(html).not.toContain('<script>')
    expect(html).not.toContain('<img')
    // 原文被转义显示，而不是被执行
    expect(html).toContain('&lt;script&gt;')
  })

  it('does not allow javascript: in link href', () => {
    const html = renderMarkdown('[x](javascript:alert(1))')
    expect(html).not.toContain('href="javascript:')
  })

  it('renders empty and plain text safely', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown('plain text')).toContain('plain text')
  })

  it('renders in-range [Sx] citations as interactive spans when sources are provided', () => {
    const html = renderMarkdown('根据 [S1] 与 [S2]，见下。', twoSources)
    expect(html).toContain('<span class="kf-cite" data-source-index="1">[S1]</span>')
    expect(html).toContain('<span class="kf-cite" data-source-index="2">[S2]</span>')
  })

  it('keeps out-of-range [Sx] citations as plain text', () => {
    const html = renderMarkdown('模型乱引用了 [S9]。', twoSources)
    expect(html).toContain('[S9]')
    expect(html).not.toContain('kf-cite')
  })

  it('keeps citations plain when no sources are provided', () => {
    const html = renderMarkdown('普通回答 [S1] 原文。')
    expect(html).toContain('[S1]')
    expect(html).not.toContain('kf-cite')
  })

  it('does not turn citations inside code blocks into interactive spans', () => {
    const html = renderMarkdown('```js\nconst ref = "[S1]"\n```', twoSources)
    expect(html).not.toContain('kf-cite')
    expect(html).toContain('[S1]')
  })

  it('renders multiple citations in one paragraph', () => {
    const html = renderMarkdown('[S1] [S2] [S2]', twoSources)
    expect(html.match(/kf-cite/g)).toHaveLength(3)
  })
})
