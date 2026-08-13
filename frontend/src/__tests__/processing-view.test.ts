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

  it('switches to the 全部 tab and lists all statuses without a status filter', async () => {
    const listSpy = vi.spyOn(processingApi, 'listProcessingTasks').mockResolvedValue([])

    const wrapper = mount(ProcessingView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    // 默认标签"处理中"：带 status 过滤
    expect(listSpy).toHaveBeenLastCalledWith('PROCESSING')

    const allLabel = wrapper.findAll('label').find((w) => w.text().includes('全部'))
    expect(allLabel).toBeDefined()
    const allInput = allLabel!.find('input[value="ALL"]')
    await allInput.setValue(true)
    await flushPromises()

    // "全部"不传 status，空态文案对应切换
    expect(listSpy).toHaveBeenLastCalledWith(undefined)
    expect(wrapper.text()).toContain('还没有任何处理任务')

    wrapper.unmount()
  })
})
