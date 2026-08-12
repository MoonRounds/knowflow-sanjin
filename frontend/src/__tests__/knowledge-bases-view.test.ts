// KnowledgeBasesView 视图测试：空态渲染、列表展示与创建流程。
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import KnowledgeBasesView from '../views/KnowledgeBasesView.vue'
import * as api from '../api/knowledge-bases'
import * as itemsApi from '../api/knowledge-items'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'

function mountView() {
  return mount(KnowledgeBasesView, { global: { plugins: [ElementPlus] } })
}

const rows: KnowledgeBaseResponse[] = [
  {
    id: '1',
    name: 'Java',
    description: 'Java notes',
    enabled: true,
    rowVersion: 0,
    createdAt: '2026-08-09T00:00:00Z',
    updatedAt: '2026-08-09T00:00:00Z',
  },
]

describe('KnowledgeBasesView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('renders an empty state when there are no knowledge bases', async () => {
    vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue([])
    vi.spyOn(itemsApi, 'listKnowledgeItems').mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.classes()).toContain('kf-list-page')
    expect(wrapper.find('.kf-list-page-header').exists()).toBe(true)
    expect(wrapper.find('.kf-list-page-actions').exists()).toBe(true)
    expect(wrapper.find('.kf-empty--wide').exists()).toBe(true)
    expect(wrapper.text()).toContain('给知识找一个长期生长的地方')
    expect(wrapper.findAll('button').some((button) => button.text().includes('新建知识库'))).toBe(
      true,
    )
  })

  it('renders the list from the API', async () => {
    vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue(rows)
    vi.spyOn(itemsApi, 'listKnowledgeItems').mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('已启用')
  })

  it('opens create dialog and creates a knowledge base', async () => {
    const listMock = vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue([])
    const createMock = vi.spyOn(api, 'createKnowledgeBase').mockResolvedValue(rows[0])
    vi.spyOn(itemsApi, 'listKnowledgeItems').mockResolvedValue([])

    const wrapper = mountView()
    await flushPromises()

    const createKbButton = wrapper.findAll('button').find((b) => b.text() === '新建知识库')
    expect(createKbButton).toBeTruthy()
    await createKbButton!.trigger('click')
    expect(wrapper.text()).toContain('新建知识库')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('Spring')
    await wrapper.find('textarea').setValue('Spring notes')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存')!
      .trigger('click')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith({ name: 'Spring', description: 'Spring notes' })
    expect(listMock).toHaveBeenCalled()
  })
})
