// ChatView 会话知识库绑定状态机测试：保留失效绑定、保存失败回滚、列表加载失败禁止保存。
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ChatView from '../views/ChatView.vue'
import * as conversationsApi from '../api/conversations'
import * as modelConfigsApi from '../api/model-configs'
import * as knowledgeBasesApi from '../api/knowledge-bases'
import type { ConversationResponse } from '../api/types/conversation'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'

function conversation(): ConversationResponse {
  return {
    id: '10',
    title: '绑定测试会话',
    knowledgeBaseIds: ['1', '2'],
    rowVersion: 1,
    createdAt: '2026-08-09T00:00:00Z',
    updatedAt: '2026-08-09T00:00:00Z',
  }
}

function knowledgeBases(): KnowledgeBaseResponse[] {
  return [
    { id: '1', name: '启用库', enabled: true, rowVersion: 0, createdAt: '', updatedAt: '' },
    { id: '2', name: '停用库', enabled: false, rowVersion: 0, createdAt: '', updatedAt: '' },
  ]
}

function popoverEditor(): HTMLElement | null {
  return document.body.querySelector('.binding-editor')
}

async function mountView(options?: { knowledgeBases?: () => Promise<KnowledgeBaseResponse[]> }) {
  vi.spyOn(conversationsApi, 'listConversations').mockResolvedValue([conversation()])
  vi.spyOn(conversationsApi, 'listMessages').mockResolvedValue({ messages: [] })
  vi.spyOn(modelConfigsApi, 'listModelConfigs').mockResolvedValue([])
  vi.spyOn(modelConfigsApi, 'getOwnerAiSettings').mockResolvedValue({
    defaultChatModelConfigId: undefined,
    utilityModelConfigId: undefined,
    updatedAt: '2026-08-09T00:00:00Z',
  })
  const kbMock = options?.knowledgeBases ?? (() => Promise.resolve(knowledgeBases()))
  vi.spyOn(knowledgeBasesApi, 'listKnowledgeBases').mockImplementation(kbMock)

  const wrapper = mount(ChatView, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

async function selectExistingConversation(wrapper: ReturnType<typeof mount>) {
  const item = wrapper.findAll('.session-item').find((b) => b.text().includes('绑定测试会话'))
  expect(item).toBeTruthy()
  await item!.trigger('click')
  await flushPromises()
}

describe('ChatView 会话知识库绑定', () => {
  beforeEach(() => {
    // ChatView 挂载时读取侧栏历史面板折叠偏好；jsdom 默认无 localStorage 全局，提供内存 stub。
    const store = new Map<string, string>()
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (k: string) => store.get(k) ?? null,
        setItem: (k: string, v: string) => store.set(k, v),
        removeItem: (k: string) => store.delete(k),
        clear: () => store.clear(),
      },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('保存时保留已停用绑定，不静默清除', async () => {
    const updateMock = vi
      .spyOn(conversationsApi, 'updateConversation')
      .mockResolvedValue(conversation())
    const wrapper = await mountView()
    await selectExistingConversation(wrapper)

    await wrapper.find('.binding-trigger').trigger('click')
    await flushPromises()
    const editor = popoverEditor()
    expect(editor).toBeTruthy()
    // 停用库以可移除芯片展示，仍保留在草稿中
    expect(editor!.textContent).toContain('停用库（已停用）')

    const saveButton = editor!.querySelector('.binding-editor-actions .el-button--primary')!
    await saveButton.dispatchEvent(new MouseEvent('click'))
    await flushPromises()

    // 完整草稿（含停用 id）随 PATCH 提交，而非过滤后只保存启用库
    expect(updateMock).toHaveBeenCalledWith(
      '10',
      expect.objectContaining({ knowledgeBaseIds: ['1', '2'], rowVersion: 1 }),
    )
  })

  it('保存失败回滚到服务器既有绑定，本地移除的失效芯片恢复', async () => {
    vi.spyOn(conversationsApi, 'updateConversation').mockRejectedValue(new Error('会话版本冲突'))
    const wrapper = await mountView()
    await selectExistingConversation(wrapper)

    await wrapper.find('.binding-trigger').trigger('click')
    await flushPromises()
    let editor = popoverEditor()
    // 本地显式移除停用库芯片
    const removeButton = editor!.querySelector('.binding-chip-remove') as HTMLElement
    expect(removeButton).toBeTruthy()
    removeButton.click()
    await flushPromises()
    expect(editor!.querySelector('.binding-chip-remove')).toBeNull()

    await editor!
      .querySelector('.binding-editor-actions .el-button--primary')!
      .dispatchEvent(new MouseEvent('click'))
    await flushPromises()

    // 保存失败后弹层保留，草稿回滚为服务器值（停用芯片重新出现）
    editor = popoverEditor()
    expect(editor).toBeTruthy()
    expect(editor!.textContent).toContain('停用库（已停用）')
  })

  it('知识库列表加载失败时禁止保存', async () => {
    vi.spyOn(conversationsApi, 'updateConversation').mockResolvedValue(conversation())
    const wrapper = await mountView({
      knowledgeBases: () => Promise.reject(new Error('网络错误')),
    })
    await selectExistingConversation(wrapper)

    await wrapper.find('.binding-trigger').trigger('click')
    await flushPromises()
    const editor = popoverEditor()
    expect(editor!.querySelector('.el-alert')).toBeTruthy()

    const saveButton = editor!.querySelector(
      '.binding-editor-actions .el-button--primary',
    ) as HTMLElement
    expect((saveButton as HTMLButtonElement).disabled).toBe(true)
    saveButton.click()
    await flushPromises()
    expect(conversationsApi.updateConversation).not.toHaveBeenCalled()
  })
})
