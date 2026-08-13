// KnowledgeBasesView（库列表）视图测试：空态渲染、列表展示（含文档数）、库名进详情、创建流程。
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import KnowledgeBasesView from '../views/KnowledgeBasesView.vue'
import * as api from '../api/knowledge-bases'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'

function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/knowledge-bases/:id', component: { template: '<div />' } }],
  })
  return {
    wrapper: mount(KnowledgeBasesView, { global: { plugins: [ElementPlus, router] } }),
    router,
  }
}

const rows: KnowledgeBaseResponse[] = [
  {
    id: '1',
    name: 'Java',
    description: 'Java notes',
    enabled: true,
    documentCount: 3,
    rowVersion: 0,
    createdAt: '2026-08-09T00:00:00Z',
    updatedAt: '2026-08-09T00:00:00Z',
  },
]

describe('KnowledgeBasesView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders an empty state when there are no knowledge bases', async () => {
    vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue([])
    const { wrapper } = mountView()
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

  it('renders the list with document counts from the API', async () => {
    vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue(rows)
    const { wrapper } = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('已启用')
    expect(wrapper.text()).toContain('3')
  })

  it('navigates to the knowledge base detail on name click', async () => {
    vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue(rows)
    const { wrapper, router } = mountView()
    await flushPromises()
    const nameLink = wrapper.find('a')
    expect(nameLink.text()).toBe('Java')
    await nameLink.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/knowledge-bases/1')
  })

  it('opens create dialog and creates a knowledge base', async () => {
    const listMock = vi.spyOn(api, 'listKnowledgeBases').mockResolvedValue([])
    const createMock = vi.spyOn(api, 'createKnowledgeBase').mockResolvedValue(rows[0])
    const { wrapper } = mountView()
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
