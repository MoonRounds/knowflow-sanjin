// Markdown 代码块 / 流程图的受控渲染基元：纯函数、无副作用，测试直接覆盖。
// 流程图识别：显式 `flow` 语言标签总是流程图；````text```` / ````plain```` / 无语言块中，
// 若「纯箭头分隔行」≥2，或 ≥1 且总非空行 ≥3，则视为流程图。
// 「纯箭头分隔行」指整行只有箭头/横线（如 ↓、→、-->），是手绘流程的典型特征，
// 能避开「a → b」这类内联箭头映射表的误判。

const NON_CODE_LANGUAGES = new Set(['', 'text', 'plain', 'txt'])

/** 整行只有箭头/横线的纯分隔行。 */
const ARROW_ONLY_LINE = /^[↓→↘⇓➜▸\-=>]+$/

export function isFlowBlock(lang: string, code: string): boolean {
  if (lang === 'flow') return true
  if (!NON_CODE_LANGUAGES.has(lang)) return false
  let pureArrowLines = 0
  let nonEmptyLines = 0
  for (const line of code.split('\n')) {
    const t = line.trim()
    if (t === '') continue
    nonEmptyLines += 1
    if (ARROW_ONLY_LINE.test(t)) pureArrowLines += 1
  }
  return pureArrowLines >= 2 || (pureArrowLines >= 1 && nonEmptyLines >= 3)
}

/** 纯分隔行：空白，或整行只有箭头/横线（在流程图中扮演步骤连接符）。 */
function isSeparatorLine(line: string): boolean {
  const t = line.trim()
  return t === '' || ARROW_ONLY_LINE.test(t)
}

/** 把流程图文本渲染成编号步骤列表；箭头/分隔行被剥离，内容 HTML 转义。 */
export function renderFlow(code: string): string {
  const steps: string[] = []
  for (const raw of code.split('\n')) {
    const line = raw.trim()
    if (isSeparatorLine(line)) continue
    steps.push(`<li>${escapeHtml(line)}</li>`)
  }
  if (steps.length === 0) return ''
  return `<ol class="kf-flow">${steps.join('')}</ol>`
}

/** 渲染带头部（语言徽标 + 复制按钮）的代码块；codeHtml 必须已转义（可含高亮 span）。 */
export function renderCodeBlock(lang: string, codeHtml: string, highlighted: boolean): string {
  const classes: string[] = []
  if (lang) classes.push(`language-${escapeHtml(lang)}`)
  if (highlighted) classes.push('hljs')
  const classAttr = classes.length > 0 ? ` class="${classes.join(' ')}"` : ''
  return (
    '<div class="kf-codeblock">' +
    `<div class="kf-codehead"><span class="kf-lang">${escapeHtml(lang || 'text')}</span>` +
    '<button type="button" class="kf-copy-btn">复制</button></div>' +
    `<pre><code${classAttr}>${codeHtml}</code></pre>` +
    '</div>'
  )
}

/** 与 markdown-it 一致的最小 HTML 转义（& < > "）。 */
export function escapeHtml(source: string): string {
  return source
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
