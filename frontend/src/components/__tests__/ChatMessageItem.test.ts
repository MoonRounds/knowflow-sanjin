// ChatMessageItem 引用交互测试（G32）：[Sx] 悬停预览 tooltip、点击展开来源面板并高亮、集合外编号纯文本。
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ChatMessageItem from '../ChatMessageItem.vue'
import type { MessageResponse } from '../../api/types/conversation'

function assistantMsg(overrides: Partial<MessageResponse> = {}): MessageResponse {
  return {
    id: '1',
    conversationId: '1',
    role: 'ASSISTANT',
    generationStatus: 'COMPLETED',
    ragStatus: 'USED',
    content: '根据 [S1] 与 [S9] 可知。',
    sources: [
      {
        sourceId: 'c1',
        documentId: '10',
        documentTitle: '并发笔记',
        knowledgeBaseId: '2',
        knowledgeBaseName: 'Java',
        sourceType: 'MANUAL_NOTE',
        snippet: 'REQUIRED 传播行为摘要',
        cited: true,
      },
    ],
    createdAt: '2026-08-09T00:00:00Z',
    ...overrides,
  }
}

async function mountItem(msg: MessageResponse) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/documents/:id', component: { template: '<div />' } },
      { path: '/knowledge-bases/:id', component: { template: '<div />' } },
    ],
  })
  return mount(ChatMessageItem, {
    props: { msg },
    global: { plugins: [ElementPlus, router] },
  })
}

describe('ChatMessageItem citations', () => {
  beforeEach(() => {
    // 只 fake setTimeout（高亮自动清除）；保留 rAF/Date 等，避免 Element Plus popper 挂起
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    document.body.innerHTML = ''
    Object.defineProperty(window.HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: vi.fn(),
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders in-range [Sx] as interactive spans and keeps out-of-range as text', async () => {
    const wrapper = await mountItem(assistantMsg())
    expect(wrapper.find('.kf-cite[data-source-index="1"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('[S9]')
    expect(wrapper.findAll('.kf-cite')).toHaveLength(1)
  })

  it('shows a tooltip with the chunk preview when hovering a citation', async () => {
    const wrapper = await mountItem(assistantMsg())
    await wrapper.find('.kf-cite').trigger('mouseover')
    await nextTick()

    const tooltip = wrapper.findComponent({ name: 'ElTooltip' })
    expect(tooltip.props('visible')).toBe(true)
    // el-tooltip 内容 teleport 到 body
    expect(document.body.textContent).toContain('并发笔记')
    expect(document.body.textContent).toContain('REQUIRED 传播行为摘要')
  })

  it('expands the sources panel and highlights the target item on citation click', async () => {
    const wrapper = await mountItem(assistantMsg())
    expect(wrapper.find('.sources-list').exists()).toBe(false)

    await wrapper.find('.kf-cite').trigger('click')
    await nextTick()

    const list = wrapper.find('.sources-list')
    expect(list.exists()).toBe(true)
    expect(list.find('.source-item.highlighted').text()).toContain('并发笔记')

    // 高亮 3 秒后自动清除
    await vi.advanceTimersByTimeAsync(3100)
    expect(wrapper.find('.source-item.highlighted').exists()).toBe(false)
  })

  it('hides the tooltip when leaving the content area', async () => {
    const wrapper = await mountItem(assistantMsg())
    await wrapper.find('.kf-cite').trigger('mouseover')
    await nextTick()
    await wrapper.find('.msg-content').trigger('mouseleave')
    await nextTick()

    expect(wrapper.findComponent({ name: 'ElTooltip' }).props('visible')).toBe(false)
  })

  it('does not expand sources for messages without citations', async () => {
    const wrapper = await mountItem(assistantMsg({ content: '普通回答，没有引用。' }))
    expect(wrapper.findAll('.kf-cite')).toHaveLength(0)
  })
})
