import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ModelSettingsView from '../views/ModelSettingsView.vue'
import * as api from '../api/model-configs'
import * as embeddingApi from '../api/embedding-configs'
import type { ModelConfigResponse, OwnerAiSettingsResponse } from '../api/types/model-config'

function mountView() {
  return mount(ModelSettingsView, { global: { plugins: [ElementPlus] } })
}

/** 补齐 Embedding 区块 API 的桩，避免 onMounted 真实请求与后续交互命中外层按钮。 */
function stubEmbeddingApi() {
  vi.spyOn(embeddingApi, 'getEmbeddingConfig').mockResolvedValue({
    configured: true,
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'text-embedding-v4',
    apiKeyMasked: 'sk-a******',
    dimension: 1024,
    updatedAt: '2026-08-09T00:00:00Z',
  })
  vi.spyOn(embeddingApi, 'testEmbeddingConfig').mockResolvedValue({
    success: true,
    message: '向量化测试通过',
    modelName: 'text-embedding-v4',
    dimension: 1024,
    testedAt: '2026-08-09T00:00:00Z',
  })
  vi.spyOn(embeddingApi, 'updateEmbeddingConfig').mockResolvedValue({
    configured: true,
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: 'text-embedding-v4',
    apiKeyMasked: 'sk-a******',
    dimension: 1024,
    updatedAt: '2026-08-09T00:00:00Z',
  })
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
    stubEmbeddingApi()
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
    stubEmbeddingApi()
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
    stubEmbeddingApi()

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).toContain('新建模型配置')

    // 限定在弹窗表单内取输入框，避免命中外层 Embedding 区块的输入
    const inputs = wrapper.find('.kf-dialog-form').findAll('input')
    await inputs[0].setValue('Qwen')
    await inputs[1].setValue('Qwen')
    await inputs[2].setValue('https://dashscope.aliyuncs.com/compatible-mode/v1')
    await inputs[3].setValue('qwen-plus')
    // password input for apiKey is the last input
    const last = inputs[inputs.length - 1]
    await last.setValue('sk-real-secret')

    const saveBtn = wrapper
      .find('.kf-dialog-actions')
      .findAll('button')
      .find((b) => b.text() === '保存')!
    await saveBtn.trigger('click')
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
    stubEmbeddingApi()
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
