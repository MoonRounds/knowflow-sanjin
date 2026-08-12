import { expect, test, type APIRequestContext } from '@playwright/test'
import {
  API_BASE,
  cleanupAll,
  configureModelViaUI,
  enterNewConversationViaUI,
  readActiveConversationId,
  createKnowledgeBaseViaUI,
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
    await createKnowledgeBaseViaUI(page, `Kf-故障恢复-${suffix}`)

    await page.getByRole('button', { name: '新建笔记' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '新建笔记' })
    await dialog
      .locator('input[placeholder="留空则取正文首行"]')
      .fill(`Kf-故障-Embedding-${suffix}`)
    await dialog
      .locator('textarea[placeholder="Markdown 正文"]')
      .fill(`Kf-故障-Embedding-${suffix}：用于验证自动重试耗尽、DLQ 与手动 Retry。`)
    await dialog.getByRole('button', { name: '创建' }).click()

    await page.waitForURL(/knowledge-items\/\d+/)
    const itemId = page.url().split('/').pop()!
    const original = await waitForTaskForBusiness(request, 'KNOWLEDGE_INDEX', itemId)
    expect(await waitForTaskTerminal(request, original.id)).toBe('FAILED')
    const failed = await getProcessingTask(request, original.id)
    expect(failed.failureCode).toBe('向量模型不可用')
    expect(failed.retryCount).toBe(failed.maxRetries)

    const failedItemResponse = await request.get(`${API_BASE}/knowledge-items/${itemId}`)
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
          const response = await request.get(`${API_BASE}/knowledge-items/${itemId}`)
          return (await response.json()).indexStatus
        },
        { timeout: 30_000 },
      )
      .toBe('INDEXED')
  })

  test('SSE 断连释放 slot；停止落 CANCELLED；重新生成切换 active attempt', async ({
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
            errorCode: assistant?.errorCode,
            activeSlot: conversation.activeGenerationMessageId ?? null,
          }
        },
        { timeout: 30_000 },
      )
      .toEqual({ status: 'FAILED', errorCode: '客户端已断开', activeSlot: null })

    await enterNewConversationViaUI(page)
    await page.locator('.chat-input textarea').fill(`Kf-慢速-停止-${suffix}`)
    await page.getByRole('button', { name: '发送' }).click()
    const stoppedConversationId = await readActiveConversationId(page)
    expect(stoppedConversationId).toBeTruthy()
    const activeAssistant = page.locator('.message.assistant').last()
    await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
    await page.getByRole('button', { name: '停止' }).click()
    await expect(activeAssistant.getByText('已取消')).toBeVisible({ timeout: 30_000 })
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
            latestStatus: assistants.at(-1)?.generationStatus,
            latestActive: assistants.at(-1)?.active,
            previousActive: assistants.find((message) => message.id === previousActive.id)?.active,
          }
        },
        { timeout: 30_000 },
      )
      .toEqual({
        count: before.filter((message) => message.role === 'ASSISTANT').length + 1,
        latestStatus: 'COMPLETED',
        latestActive: true,
        previousActive: false,
      })
  })
})
