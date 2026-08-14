import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'
import MessageSourcesPanel from '../MessageSourcesPanel.vue'
import type { RetrievedSource } from '../../api/types/conversation'

function mountPanel(props: {
  ragStatus?: string
  sources?: RetrievedSource[]
  expanded?: boolean
  highlightIndex?: number
}) {
  return mount(MessageSourcesPanel, { props, global: { plugins: [ElementPlus] } })
}

const sources = [
  {
    sourceId: 'source-1',
    documentId: '1',
    documentTitle: '并发笔记',
    sourceType: 'MANUAL_NOTE',
    knowledgeBaseId: '10',
    knowledgeBaseName: 'Java 知识库',
    snippet: '摘要一',
    cited: true,
  },
  {
    sourceId: 'source-2',
    documentId: '2',
    documentTitle: '线程笔记',
    snippet: '摘要二',
    cited: false,
  },
] as RetrievedSource[]

describe('MessageSourcesPanel', () => {
  it.each(['NOT_AVAILABLE', 'NOT_NEEDED'])('hides routine status %s', (ragStatus) => {
    const wrapper = mountPanel({ ragStatus })
    expect(wrapper.text()).toBe('')
  })

  it('shows one compact personal knowledge summary when sources exist', () => {
    const wrapper = mountPanel({ ragStatus: 'USED', sources })

    expect(wrapper.text()).toContain('个人知识 · 来源 1/2')
    expect(wrapper.text()).not.toContain('已使用个人知识')
  })

  it('keeps degraded status visible', () => {
    const wrapper = mountPanel({ ragStatus: 'DEGRADED' })
    expect(wrapper.text()).toContain('检索降级')
  })

  it('lists sources with knowledge base name and source type when expanded', () => {
    const wrapper = mountPanel({ ragStatus: 'USED', sources, expanded: true })

    expect(wrapper.text()).toContain('[S1]')
    expect(wrapper.text()).toContain('并发笔记')
    expect(wrapper.text()).toContain('Java 知识库')
    expect(wrapper.text()).toContain('笔记')
    expect(wrapper.text()).toContain('已引用')
  })

  it('hides knowledge base name when the source lacks it (legacy traces)', () => {
    const wrapper = mountPanel({ ragStatus: 'USED', sources, expanded: true })

    // 第二条来源无 knowledgeBaseId：不渲染 KB 链接、不显示占位符
    expect(wrapper.text()).not.toContain('未知知识库')
  })

  it('highlights the target source item by 1-based index', () => {
    const wrapper = mountPanel({ ragStatus: 'USED', sources, expanded: true, highlightIndex: 2 })

    const items = wrapper.findAll('.source-item')
    expect(items[1].classes()).toContain('highlighted')
    expect(items[0].classes()).not.toContain('highlighted')
  })

  it('emits toggle when the badge is clicked', async () => {
    const wrapper = mountPanel({ ragStatus: 'USED', sources })
    await wrapper.find('.status-badge').trigger('click')
    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })
})
