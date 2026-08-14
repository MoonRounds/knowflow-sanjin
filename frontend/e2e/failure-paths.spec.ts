import { expect, test, type APIRequestContext } from '@playwright/test'
import {
  API_BASE,
  cleanupAll,
  configureModelViaUI,
  enterNewConversationViaUI,
  readActiveConversationId,
  createKnowledgeBaseViaUI,
  openKnowledgeBaseDetailViaUI,
  getProcessingTask,
  sendMessage,
  waitForTaskForBusiness,
  waitForTaskTerminal,
} from './helpers'

interface MessageView {
  id: string
  role: string
  generationStatus?: string
  errorCode?: string
  active?: boolean
  content?: string
}

async function readMessages(
  request: APIRequestContext,
  conversationId: string,
): Promise<MessageView[]> {
  const response = await request.get(
    `${API_BASE}/conversations/${conversationId}/messages?limit=20`,
  )
  expect(response.ok()).toBeTruthy()
  return ((await response.json()).messages ?? []) as MessageView[]
}

test.describe('受控失败与恢复', () => {
  test.beforeEach(async ({ request }) => {
    await cleanupAll(request)
  })

  test('Embedding 自动重试耗尽后可从 Processing 页面手动恢复', async ({
    page,
    request,
  }, testInfo) => {
    const suffix = `r${testInfo.retry}`
    const kb = await createKnowledgeBaseViaUI(page, `Kf-故障恢复-${suffix}`)

    // ---- 库详情页新建 Manual Note（P2 入口迁移）----
    await openKnowledgeBaseDetailViaUI(page, kb.name)
    await page.getByRole('button', { name: '新建笔记' }).first().click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '新建笔记' })
    await dialog
      .locator('input[placeholder="留空则取正文首行"]')
      .fill(`Kf-故障-Embedding-${suffix}`)
    await dialog
      .locator('textarea[placeholder="Markdown 正文"]')
      .fill(`Kf-故障-Embedding-${suffix}：用于验证自动重试耗尽、DLQ 与手动 Retry。`)
    await dialog.getByRole('button', { name: '创建' }).click()

    await page.waitForURL(/documents\/\d+/)
    const itemId = page.url().split('/').pop()!
    const original = await waitForTaskForBusiness(request, 'KNOWLEDGE_INDEX', itemId)
    expect(await waitForTaskTerminal(request, original.id)).toBe('FAILED')
    const failed = await getProcessingTask(request, original.id)
    expect(failed.failureCode).toBe('向量模型不可用')
    expect(failed.retryCount).toBe(failed.maxRetries)

    const failedItemResponse = await request.get(`${API_BASE}/documents/${itemId}`)
    expect(await failedItemResponse.json()).toEqual(
      expect.objectContaining({ indexStatus: 'FAILED', indexErrorCode: '向量模型不可用' }),
    )

    await page.goto('/processing')
    await page.locator('.el-radio-button').filter({ hasText: '失败' }).click()
    const row = page.locator('.el-table__row').filter({ hasText: original.id })
    await expect(row).toContainText('向量模型不可用')
    const retryResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith(`/api/v1/processing-tasks/${original.id}/retry`),
    )
    await row.getByRole('button', { name: '重试' }).click()
    const retryResponse = await retryResponsePromise
    const retry = await retryResponse.json()
    expect(retryResponse.ok(), JSON.stringify(retry)).toBeTruthy()
    expect(retry.retryOfTaskId).toBe(original.id)
    expect(await waitForTaskTerminal(request, retry.id)).toBe('SUCCEEDED')

    await expect
      .poll(
        async () => {
          const response = await request.get(`${API_BASE}/documents/${itemId}`)
          return (await response.json()).indexStatus
        },
        { timeout: 30_000 },
      )
      .toBe('INDEXED')
  })

  test('SSE 断连不中断生成；停止落 CANCELLED；重新生成原位覆盖 active 消息', async ({
    page,
    request,
  }, testInfo) => {
    const suffix = `r${testInfo.retry}`
    await configureModelViaUI(page, `stub-chat-failure-${suffix}`)

    await enterNewConversationViaUI(page)
    await page.locator('.chat-input textarea').fill(`Kf-慢速-断连-${suffix}`)
    await page.getByRole('button', { name: '发送' }).click()
    await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
    const disconnectedConversationId = await readActiveConversationId(page)
    expect(disconnectedConversationId).toBeTruthy()
    let disconnectedAssistantId = ''
    await expect
      .poll(
        async () => {
          const messages = await readMessages(request, disconnectedConversationId)
          disconnectedAssistantId =
            messages.filter((message) => message.role === 'ASSISTANT').at(-1)?.id ?? ''
          return disconnectedAssistantId
        },
        { timeout: 30_000 },
      )
      .not.toBe('')
    await page.goto('/knowledge-bases')
    // 断连后后端进入静默模式：Provider 流继续收完，回答以 COMPLETED 落库并释放 active slot
    await expect
      .poll(
        async () => {
          const [conversationResponse, messagesResponse] = await Promise.all([
            request.get(`${API_BASE}/conversations/${disconnectedConversationId}`),
            request.get(
              `${API_BASE}/conversations/${disconnectedConversationId}/messages?limit=20`,
            ),
          ])
          const conversation = await conversationResponse.json()
          const messages = ((await messagesResponse.json()).messages ?? []) as MessageView[]
          const assistant = messages.find((message) => message.id === disconnectedAssistantId)
          return {
            status: assistant?.generationStatus,
            content: assistant?.content,
            activeSlot: conversation.activeGenerationMessageId ?? null,
          }
        },
        { timeout: 60_000 },
      )
      .toEqual(expect.objectContaining({ status: 'COMPLETED', activeSlot: null }))

    await enterNewConversationViaUI(page)
    await page.locator('.chat-input textarea').fill(`Kf-慢速-停止-${suffix}`)
    await page.getByRole('button', { name: '发送' }).click()
    const stoppedConversationId = await readActiveConversationId(page)
    expect(stoppedConversationId).toBeTruthy()
    await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
    await page.getByRole('button', { name: '停止' }).click()
    await expect
      .poll(
        async () => {
          const [conversationResponse, messagesResponse] = await Promise.all([
            request.get(`${API_BASE}/conversations/${stoppedConversationId}`),
            request.get(`${API_BASE}/conversations/${stoppedConversationId}/messages?limit=20`),
          ])
          const conversation = await conversationResponse.json()
          const messages = ((await messagesResponse.json()).messages ?? []) as MessageView[]
          return {
            status: messages.filter((message) => message.role === 'ASSISTANT').at(-1)
              ?.generationStatus,
            activeSlot: conversation.activeGenerationMessageId ?? null,
          }
        },
        { timeout: 30_000 },
      )
      .toEqual({ status: 'CANCELLED', activeSlot: null })

    await sendMessage(page, '你好', '你好')
    const beforeResponse = await request.get(
      `${API_BASE}/conversations/${stoppedConversationId}/messages?limit=20`,
    )
    const before = ((await beforeResponse.json()).messages ?? []) as MessageView[]
    const previousActive = before.filter((message) => message.role === 'ASSISTANT').at(-1)!

    await page
      .locator('.message.assistant')
      .last()
      .getByRole('button', { name: '重新生成' })
      .click()
    await expect
      .poll(
        async () => {
          const response = await request.get(
            `${API_BASE}/conversations/${stoppedConversationId}/messages?limit=20`,
          )
          const messages = ((await response.json()).messages ?? []) as MessageView[]
          const assistants = messages.filter((message) => message.role === 'ASSISTANT')
          return {
            count: assistants.length,
            latestId: assistants.at(-1)?.id,
            latestStatus: assistants.at(-1)?.generationStatus,
            latestActive: assistants.at(-1)?.active,
          }
        },
        { timeout: 30_000 },
      )
      .toEqual({
        count: before.filter((message) => message.role === 'ASSISTANT').length,
        latestId: previousActive.id,
        latestStatus: 'COMPLETED',
        latestActive: true,
      })
  })

  test('生成中切换模块后返回：侧栏标记生成中，后台完成后看到完整回答', async ({
    page,
  }, testInfo) => {
    const suffix = `r${testInfo.retry}`
    await configureModelViaUI(page, `stub-chat-bg-${suffix}`)

    await enterNewConversationViaUI(page)
    await page.locator('.chat-input textarea').fill(`Kf-慢速-后台完成-${suffix}`)
    await page.getByRole('button', { name: '发送' }).click()
    await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
    const conversationId = await readActiveConversationId(page)
    expect(conversationId).toBeTruthy()

    // 生成中通过侧栏导航切到知识库模块（SPA 路由切换，页面不刷新：store 与本地流存活）
    await page.getByRole('link', { name: '知识库' }).click()
    await expect(page).toHaveURL(/\/knowledge-bases/)

    // 返回 /chat：该会话仍在后台生成，侧栏会话项显示「生成中」标记（store 跨路由存活）
    await page.getByRole('link', { name: 'AI 对话' }).click()
    await expect(page).toHaveURL(/\/chat/)
    await expect(page.locator('.session-generating').first()).toBeVisible({
      timeout: 10_000,
    })

    // 进入该会话（排除空白态占位项）：实时增量内容（store live 消息）最终汇聚为完整回答
    await page.locator('.session-item:not([aria-current])').first().click()
    await expect
      .poll(
        () =>
          page
            .locator('.message.assistant .msg-content')
            .allTextContents()
            .then((texts) => texts.some((t) => t.includes('Kf-慢速回答-'))),
        { timeout: 60_000 },
      )
      .toBe(true)

    // 生成完成并落库后，「生成中」标记消失
    await expect(page.locator('.session-generating')).toHaveCount(0, { timeout: 30_000 })
  })
})
