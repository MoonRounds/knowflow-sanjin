// 受控 Markdown 渲染测试：确认基本语法被渲染、raw HTML 被禁用（安全边界）。
import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '../markdown'

describe('renderMarkdown', () => {
  it('renders headings, bold, inline code and code blocks', () => {
    const html = renderMarkdown('# 标题\n\n**加粗** 和 `inline`\n\n```js\nconst a = 1\n```')
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<strong>加粗</strong>')
    expect(html).toContain('<code>inline</code>')
    expect(html).toContain('<pre><code class="language-js">')
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
})
