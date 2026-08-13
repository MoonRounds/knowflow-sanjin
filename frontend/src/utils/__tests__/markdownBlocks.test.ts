// markdownBlocks 纯函数测试：流程图识别与渲染、代码块骨架、HTML 转义。
import { describe, expect, it } from 'vitest'
import { escapeHtml, isFlowBlock, renderCodeBlock, renderFlow } from '../markdownBlocks'

const FLOW_SAMPLE = '用户提问\n  ↓\nAgent 理解意图\n  ↓\n系统执行 Skill'

describe('isFlowBlock', () => {
  it('accepts explicit flow language regardless of content', () => {
    expect(isFlowBlock('flow', 'anything')).toBe(true)
  })

  it('detects arrow-rich text/plain/empty-language blocks', () => {
    expect(isFlowBlock('', FLOW_SAMPLE)).toBe(true)
    expect(isFlowBlock('text', 'a\n↓\nb\n↓\nc')).toBe(true)
    expect(isFlowBlock('plain', 'a\n→\nb')).toBe(true)
  })

  it('rejects text blocks without arrow separator lines', () => {
    expect(isFlowBlock('text', '一行\n两行')).toBe(false)
    expect(isFlowBlock('', '普通文本')).toBe(false)
    // 内联箭头（映射表样式）不是纯箭头分隔行，不误判为流程图
    expect(isFlowBlock('text', 'a → b')).toBe(false)
  })

  it('never treats real code languages as flow', () => {
    expect(isFlowBlock('js', 'const a = 1\nconst b = 2')).toBe(false)
    expect(isFlowBlock('sql', 'select 1\n-> whatever')).toBe(false)
  })

  it('requires enough structure: single arrow line needs at least 3 non-empty lines', () => {
    expect(isFlowBlock('text', 'a\n↓')).toBe(false)
    expect(isFlowBlock('text', 'a\n↓\nb')).toBe(true)
  })
})

describe('renderFlow', () => {
  it('renders numbered steps and strips separators', () => {
    expect(renderFlow(FLOW_SAMPLE)).toBe(
      '<ol class="kf-flow"><li>用户提问</li><li>Agent 理解意图</li><li>系统执行 Skill</li></ol>',
    )
  })

  it('escapes step content', () => {
    expect(renderFlow('<b> & "x"\n↓\nnext')).toContain('<li>&lt;b&gt; &amp; &quot;x&quot;</li>')
  })

  it('returns empty string when nothing remains after stripping separators', () => {
    expect(renderFlow('↓\n→')).toBe('')
  })
})

describe('renderCodeBlock', () => {
  it('builds head with language badge and copy button', () => {
    const html = renderCodeBlock('js', '<span>x</span>', true)
    expect(html).toContain('<div class="kf-codeblock">')
    expect(html).toContain('<span class="kf-lang">js</span>')
    expect(html).toContain('<button type="button" class="kf-copy-btn">复制</button>')
    expect(html).toContain('<code class="language-js hljs">')
  })

  it('omits language class when lang is empty and highlighting not applied', () => {
    const html = renderCodeBlock('', 'plain', false)
    expect(html).toContain('<pre><code>plain</code></pre>')
    expect(html).toContain('<span class="kf-lang">text</span>')
  })

  it('escapes lang and code content', () => {
    const html = renderCodeBlock('<x>', '<&>', false)
    expect(html).toContain('<span class="kf-lang">&lt;x&gt;</span>')
    expect(html).toContain('<code class="language-&lt;x&gt;">')
  })
})

describe('escapeHtml', () => {
  it('escapes & < > "', () => {
    expect(escapeHtml('& < > "')).toBe('&amp; &lt; &gt; &quot;')
  })
})
