import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CandidatesView from '../views/CandidatesView.vue'
import * as extractionApi from '../api/extraction'
import * as knowledgeBasesApi from '../api/knowledge-bases'

describe('CandidatesView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('uses the shared list-page template and wide empty state', async () => {
    vi.spyOn(knowledgeBasesApi, 'listKnowledgeBases').mockResolvedValue([])
    vi.spyOn(extractionApi, 'listCandidates').mockResolvedValue({
      items: [],
      page: 1,
      size: 20,
      total: 0,
    })

    const wrapper = mount(CandidatesView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.classes()).toContain('kf-list-page')
    expect(wrapper.find('.kf-list-page-header').exists()).toBe(true)
    expect(wrapper.find('.kf-list-page-actions').exists()).toBe(true)
    expect(wrapper.find('.kf-empty--wide').exists()).toBe(true)
    expect(wrapper.text()).toContain('还没有等待沉淀的知识')
  })
})
