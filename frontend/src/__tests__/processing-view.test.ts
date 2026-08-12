import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as processingApi from '../api/processing-tasks'
import ProcessingView from '../views/ProcessingView.vue'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

describe('ProcessingView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    push.mockReset()
  })

  it('uses the shared list-page template and wide empty state', async () => {
    vi.spyOn(processingApi, 'listProcessingTasks').mockResolvedValue([])

    const wrapper = mount(ProcessingView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.classes()).toContain('kf-list-page')
    expect(wrapper.find('.kf-list-page-header').exists()).toBe(true)
    expect(wrapper.find('.kf-list-page-actions').exists()).toBe(true)
    expect(wrapper.find('.kf-empty--wide').exists()).toBe(true)
    expect(wrapper.text()).toContain('此刻没有正在处理的内容')

    wrapper.unmount()
  })
})
