import { expect, test } from '@playwright/test'
import {
  API_BASE,
  cleanupAll,
  configureModelViaUI,
  createKnowledgeBaseViaUI,
  enterNewConversationViaUI,
  readActiveConversationId,
  sendMessage,
  waitForItemIndexed,
  waitForLatestTask,
  waitForTaskTerminal,
} from './helpers'

interface MessageView {
  id?: string
  role?: string
  content?: string
  generationStatus?: string
  modelConfigId?: string
  revisionNo?: number
  ragStatus?: string
  sources?: Array<{
    itemId?: string
    itemTitle?: string
    sourceType?: string
    cited?: boolean
  }>
}

interface CandidateView {
  id?: string
  extractionTaskId?: string
  confirmedItemId?: string
  draftKnowledgeBaseIds?: string[]
}

/**
 * 闭环 1：对话沉淀闭环 E2E。
 *
 * 通过正式前端完成：配置模型 → 创建 KB/会话 → SSE 多轮对话 → 触发提取 → 编辑并确认
 * Candidate → 创建 Item → 异步索引成功 → 新会话 Router+RAG 检索刚沉淀的个人知识。
 *
 * 模型/Embedding 全部使用本地 stub（scripts/model-stub.py），固定可重复。
 * 数据命名带 Kf- 前缀，避免与通用模型知识混淆。
 */
test.describe('对话沉淀闭环', () => {
  test.beforeEach(async ({ request }) => {
    await cleanupAll(request)
  })

  test('会话沉淀为知识并被新会话检索到', async ({ page, request }, testInfo) => {
    const suffix = `r${testInfo.retry}`

    // ---- 正式 UI 准备：模型配置/能力测试/Owner 默认 + KB ----
    const model = await configureModelViaUI(page, `stub-chat-conversation-${suffix}`)
    const kb = await createKnowledgeBaseViaUI(page, `Kf-后端工程规范-${suffix}`)

    // ---- 创建会话并完成两轮 SSE；第二轮必须依赖第一轮的海豚上下文 ----
    await enterNewConversationViaUI(page)
    const modelSelect = page.locator('.chat-core-head .model-select')
    await modelSelect.click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: model.name }).click()
    await expect(modelSelect).toContainText(model.name)
    await sendMessage(page, 'Kf-海豚-部署前必须备份哪三样东西？', '数据库、配置文件、原始数据')
    const sourceConversationId = await readActiveConversationId(page)
    expect(sourceConversationId).toBeTruthy()
    await expect
      .poll(async () => {
        const response = await request.get(`${API_BASE}/conversations`)
        const conversations = (await response.json()) as Array<{
          id?: string
          defaultModelConfigId?: string
        }>
        return conversations.find((conversation) => conversation.id === sourceConversationId)
          ?.defaultModelConfigId
      })
      .toBe(model.id)
    await sendMessage(page, '那回滚预案怎么定？', 'Kf-海豚-回滚预案')

    const sourceConversationResponse = await request.get(
      `${API_BASE}/conversations/${sourceConversationId}/messages?limit=20`,
    )
    expect(sourceConversationResponse.ok()).toBeTruthy()
    const sourceMessages = ((await sourceConversationResponse.json()).messages ??
      []) as MessageView[]
    expect(sourceMessages).toHaveLength(4)
    const sourceAssistants = sourceMessages.filter((message) => message.role === 'ASSISTANT')
    expect(sourceAssistants).toHaveLength(2)
    for (const assistant of sourceAssistants) {
      expect(assistant.generationStatus).toBe('COMPLETED')
      expect(assistant.modelConfigId).toBe(model.id)
      expect(assistant.revisionNo).toBeGreaterThan(0)
    }
    expect(sourceAssistants[1].content).toContain('Kf-海豚-回滚预案')

    // ---- 触发知识提取；cutoff 必须覆盖第二轮完整 assistant ----
    const extractionResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        response.url().endsWith(`/api/v1/conversations/${sourceConversationId}/extraction`),
    )
    await page.getByRole('button', { name: '提取知识' }).click()
    const extractionResponse = await extractionResponsePromise
    expect(extractionResponse.ok()).toBeTruthy()
    const extraction = await extractionResponse.json()
    expect(extraction.cutoffMessageId).toBe(sourceAssistants[1].id)
    expect(extraction.inputCharCount).toBeGreaterThan(0)

    const extractionTask = await waitForLatestTask(request, 'EXTRACTION')
    expect(extractionTask.id).toBe(extraction.processingTaskId)
    expect(await waitForTaskTerminal(request, extractionTask.id)).toBe('SUCCEEDED')

    const candidatesResponse = await request.get(
      `${API_BASE}/candidates?status=PENDING&page=1&size=20`,
    )
    expect(candidatesResponse.ok()).toBeTruthy()
    const candidates = ((await candidatesResponse.json()).items ?? []) as CandidateView[]
    const matchingCandidates = candidates.filter(
      (candidate) => candidate.extractionTaskId === extraction.id,
    )
    expect(matchingCandidates).toHaveLength(1)
    const candidate = matchingCandidates[0]
    expect(candidate.id).toBeTruthy()
    expect(candidate.draftKnowledgeBaseIds).toContain(kb.id)

    // ---- 正式 Candidates 页查看、编辑、保存并确认 ----
    const editedTitle = `Kf-海豚-部署备份与回滚-${suffix}`
    const editedContent =
      '海豚部署前必须备份：数据库、配置文件、原始数据。回滚时恢复数据库备份并回退配置。'
    await page.goto('/candidates')
    const candidateCard = page
      .locator('.candidate-card')
      .filter({ hasText: `候选 #${candidate.id}` })
    await expect(candidateCard).toBeVisible()
    await candidateCard.getByRole('button', { name: '查看/编辑' }).click()
    const drawer = page.locator('.el-drawer')
    await drawer.getByRole('button', { name: '编辑草稿' }).click()
    await drawer.getByTestId('draft-title').fill(editedTitle)
    await drawer.getByTestId('draft-content').fill(editedContent)
    const saveDraftResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().endsWith(`/api/v1/candidates/${candidate.id}/draft`),
    )
    await drawer.getByRole('button', { name: '保存草稿' }).click()
    const saveDraftResponse = await saveDraftResponsePromise
    expect(
      saveDraftResponse.ok(),
      `save candidate draft failed: ${await saveDraftResponse.text()}`,
    ).toBeTruthy()
    await expect(drawer.getByRole('button', { name: '编辑草稿' })).toBeVisible()
    await drawer.getByRole('button', { name: '确认' }).click()
    await page.waitForURL(/knowledge-items\/\d+/)

    const itemId = page.url().split('/').pop()!
    expect(itemId).toBeTruthy()
    await expect(page.getByRole('heading', { name: editedTitle })).toBeVisible()

    // 重复 confirm 必须返回同一 Item，且不能增加 KnowledgeItem 数量。
    const itemsBeforeResponse = await request.get(`${API_BASE}/knowledge-items`)
    const itemIdsBefore = ((await itemsBeforeResponse.json()) as Array<{ id: string }>)
      .map((item) => item.id)
      .sort()
    const repeatedConfirm = await request.post(`${API_BASE}/candidates/${candidate.id}/confirm`)
    expect(repeatedConfirm.ok()).toBeTruthy()
    expect((await repeatedConfirm.json()).confirmedItemId).toBe(itemId)
    const itemsAfterResponse = await request.get(`${API_BASE}/knowledge-items`)
    const itemIdsAfter = ((await itemsAfterResponse.json()) as Array<{ id: string }>)
      .map((item) => item.id)
      .sort()
    expect(itemIdsAfter).toEqual(itemIdsBefore)

    await waitForItemIndexed(request, itemId)

    // ---- 新会话 C-Q1：Router/RAG 使用刚确认的 Item，并留下可追溯 cited 来源 ----
    await enterNewConversationViaUI(page)
    await sendMessage(page, 'Kf-海豚-部署前必须备份哪三样东西？', '数据库、配置文件、原始数据')
    const ragConversationId = await readActiveConversationId(page)
    expect(ragConversationId).toBeTruthy()

    let latestAssistant = page.locator('.message.assistant').last()
    await expect(latestAssistant.getByRole('button', { name: /个人知识 · 来源/ })).toBeVisible()
    await latestAssistant.getByRole('button', { name: /来源/ }).click()
    const sourceItem = latestAssistant.locator('.source-item').filter({ hasText: editedTitle })
    await expect(sourceItem).toBeVisible()
    await expect(sourceItem.getByText('已引用')).toBeVisible()

    let ragMessagesResponse = await request.get(
      `${API_BASE}/conversations/${ragConversationId}/messages?limit=20`,
    )
    let ragMessages = ((await ragMessagesResponse.json()).messages ?? []) as MessageView[]
    let ragAssistant = ragMessages.filter((message) => message.role === 'ASSISTANT').at(-1)!
    expect(ragAssistant.ragStatus).toBe('USED')
    expect(ragAssistant.sources).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          itemId,
          itemTitle: editedTitle,
          sourceType: 'AI_CONVERSATION',
          cited: true,
        }),
      ]),
    )

    // ---- C-Q2：通用问候不触发 RAG，也不携带来源 ----
    await sendMessage(page, '你好', '你好')
    latestAssistant = page.locator('.message.assistant').last()
    await expect(latestAssistant.locator('.sources-panel')).toHaveCount(0)

    ragMessagesResponse = await request.get(
      `${API_BASE}/conversations/${ragConversationId}/messages?limit=20`,
    )
    ragMessages = ((await ragMessagesResponse.json()).messages ?? []) as MessageView[]
    ragAssistant = ragMessages.filter((message) => message.role === 'ASSISTANT').at(-1)!
    expect(ragAssistant.ragStatus).toBe('NOT_NEEDED')
    expect(ragAssistant.sources ?? []).toHaveLength(0)
  })
})
