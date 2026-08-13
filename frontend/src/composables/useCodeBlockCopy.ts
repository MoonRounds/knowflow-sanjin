// 代码块复制：事件委托在 v-html 容器 @click 上，命中 .kf-copy-btn 时复制对应 <code> 文本。
import { onBeforeUnmount } from 'vue'

export function useCodeBlockCopy() {
  let resetTimer: number | undefined

  function handleClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null
    if (!target || !target.classList.contains('kf-copy-btn')) return
    const codeBlock = target.closest<HTMLElement>('.kf-codeblock')
    const text = codeBlock?.querySelector<HTMLElement>('code')?.textContent ?? ''
    if (text === '') return
    if (!navigator.clipboard) return
    navigator.clipboard
      .writeText(text)
      .then(() => {
        const original = target.textContent ?? '复制'
        target.textContent = '已复制'
        window.clearTimeout(resetTimer)
        resetTimer = window.setTimeout(() => {
          target.textContent = original
        }, 1600)
      })
      .catch(() => {
        /* 剪贴板写入失败时静默回退 */
      })
  }

  onBeforeUnmount(() => window.clearTimeout(resetTimer))

  return { handleClick }
}
