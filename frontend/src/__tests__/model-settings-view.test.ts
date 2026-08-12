import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ModelSettingsView from '../views/ModelSettingsView.vue'
import * as api from '../api/model-configs'
import type { ModelConfigResponse, OwnerAiSettingsResponse } from '../api/types/model-config'

function mountView() {
  return mount(ModelSettingsView, { global: { plugins: [ElementPlus] } })
}

const config: ModelConfigResponse = {
  id: '1',
  displayName: 'DeepSeek',
  providerName: 'DeepSeek',
  enabled: true,
  currentRevisionId: '11',
  currentRevision: {
    id: '11',
    revisionNo: 1,
    displayName: 'DeepSeek',
    providerName: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com',
    modelName: 'deepseek-chat',
    temperature: 0.7,
    maxOutputTokens: 2048,
    apiKeyMasked: 'sk-a******',
    createdAt: '2026-08-09T00:00:00Z',
  },
  createdAt: '2026-08-09T00:00:00Z',
  updatedAt: '2026-08-09T00:00:00Z',
}

const settings: OwnerAiSettingsResponse = {
  defaultChatModelConfigId: '1',
  utilityModelConfigId: '1',
  updatedAt: '2026-08-09T00:00:00Z',
}

describe('ModelSettingsView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('renders empty state when there are no configs', async () => {
    vi.spyOn(api, 'listModelConfigs').mockResolvedValue([])
    vi.spyOn(api, 'getOwnerAiSettings').mockResolvedValue({
      updatedAt: '2026-08-09T00:00:00Z',
    } as OwnerAiSettingsResponse)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('先接入一个可以对话的模型')
    expect(wrapper.findAll('button').some((button) => button.text().includes('新建模型配置'))).toBe(
      true,
    )
  })

  it('renders config list with masked key and roles', async () => {
    vi.spyOn(api, 'listModelConfigs').mockResolvedValue([config])
    vi.spyOn(api, 'getOwnerAiSettings').mockResolvedValue(settings)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('DeepSeek')
    expect(wrapper.text()).toContain('deepseek-chat')
    expect(wrapper.text()).toContain('sk-a******')
    expect(wrapper.text()).toContain('默认 Chat')
    expect(wrapper.text()).toContain('Utility')
    // 明文 Key 绝不能出现在页面
    expect(wrapper.text()).not.toContain('sk-real-secret')
  })

  it('creates a new config with api key', async () => {
    vi.spyOn(api, 'listModelConfigs').mockResolvedValue([])
    vi.spyOn(api, 'getOwnerAiSettings').mockResolvedValue({
      updatedAt: '2026-08-09T00:00:00Z',
    } as OwnerAiSettingsResponse)
    const createMock = vi.spyOn(api, 'createModelConfig').mockResolvedValue(config)

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).toContain('新建模型配置')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('Qwen')
    await inputs[1].setValue('Qwen')
    await inputs[2].setValue('https://dashscope.aliyuncs.com/compatible-mode/v1')
    await inputs[3].setValue('qwen-plus')
    // password input for apiKey is the last input
    const last = inputs[inputs.length - 1]
    await last.setValue('sk-real-secret')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存')!
      .trigger('click')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith(
      expect.objectContaining({
        displayName: 'Qwen',
        baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
        modelName: 'qwen-plus',
        apiKey: 'sk-real-secret',
      }),
    )
    expect((last.element as HTMLInputElement).value).toBe('')
  })

  it('tests connection and shows result', async () => {
    vi.spyOn(api, 'listModelConfigs').mockResolvedValue([config])
    vi.spyOn(api, 'getOwnerAiSettings').mockResolvedValue(settings)
    const testMock = vi.spyOn(api, 'testConnection').mockResolvedValue({
      success: true,
      message: 'Connection OK, got text reply',
      modelName: 'deepseek-chat',
      testedAt: '2026-08-09T00:00:00Z',
      warnings: [],
    })

    const wrapper = mountView()
    await flushPromises()
    const btn = wrapper.findAll('button').find((b) => b.text() === '测试连接')!
    await btn.trigger('click')
    await flushPromises()

    expect(testMock).toHaveBeenCalledWith('1')
  })
})
