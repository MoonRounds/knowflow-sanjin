import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import MessageSourcesPanel from '../MessageSourcesPanel.vue'
import type { RetrievedSource } from '../../api/types/conversation'

function mountPanel(props: { ragStatus?: string; sources?: RetrievedSource[] }) {
  return mount(MessageSourcesPanel, { props, global: { plugins: [ElementPlus] } })
}

describe('MessageSourcesPanel', () => {
  it.each(['NOT_AVAILABLE', 'NOT_NEEDED'])('hides routine status %s', (ragStatus) => {
    const wrapper = mountPanel({ ragStatus })
    expect(wrapper.text()).toBe('')
  })

  it('shows one compact personal knowledge summary when sources exist', () => {
    const sources = [
      {
        sourceId: 'source-1',
        itemId: '1',
        itemTitle: '并发笔记',
        snippet: '摘要',
        cited: true,
      },
      {
        sourceId: 'source-2',
        itemId: '2',
        itemTitle: '线程笔记',
        snippet: '摘要',
        cited: false,
      },
    ] as RetrievedSource[]
    const wrapper = mountPanel({ ragStatus: 'USED', sources })

    expect(wrapper.text()).toContain('个人知识 · 来源 1/2')
    expect(wrapper.text()).not.toContain('已使用个人知识')
  })

  it('keeps degraded status visible', () => {
    const wrapper = mountPanel({ ragStatus: 'DEGRADED' })
    expect(wrapper.text()).toContain('检索降级')
  })
})
