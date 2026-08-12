import { expect, test } from '@playwright/test'
import {
  API_BASE,
  cleanupAll,
  configureModelViaUI,
  createConversationViaUI,
  createKnowledgeBaseViaUI,
  sendMessage,
  waitForItemIndexed,
  waitForLatestTask,
  waitForTaskTerminal,
} from './helpers'

interface MessageView {
  role?: string
  ragStatus?: string
  sources?: Array<{
    itemId?: string
    itemTitle?: string
    sourceType?: string
    cited?: boolean
  }>
}

/**
 * 闭环 2：个人笔记/上传闭环 E2E。
 *
 * 通过正式前端完成：创建 KB → Manual Note 或上传 Markdown → 形成 KnowledgeItem
 * → 异步索引成功 → 新会话 Router+RAG 检索个人笔记 → 前端追溯来源。
 */
test.describe('个人笔记/上传闭环', () => {
  test.beforeEach(async ({ request }) => {
    await cleanupAll(request)
  })

  test('Manual Note 沉淀并被新会话检索到', async ({ page, request }, testInfo) => {
    const suffix = `r${testInfo.retry}`
    await configureModelViaUI(page, `stub-chat-manual-${suffix}`)
    const kb = await createKnowledgeBaseViaUI(page, `Kf-个人知识管理-${suffix}`)

    // ---- 正式 Knowledge 页面新建 Manual Note ----
    await page.getByRole('button', { name: '新建笔记' }).click()
    const noteDialog = page.locator('.el-dialog').filter({ hasText: '新建笔记' })
    await noteDialog
      .locator('input[placeholder="留空则取正文首行"]')
      .fill(`Kf-番茄工作法-个人实践-${suffix}`)
    await noteDialog
      .locator('textarea[placeholder="Markdown 正文"]')
      .fill('我实践番茄工作法时固定使用 45 分钟工作 + 10 分钟休息。')
    await noteDialog.getByRole('button', { name: '创建' }).click()

    await page.waitForURL(/knowledge-items\/\d+/)
    const itemId = page.url().split('/').pop()!
    expect(itemId).toBeTruthy()
    await expect(page.getByText('来源：MANUAL_NOTE')).toBeVisible()

    await waitForItemIndexed(request, itemId)
    const itemResponse = await request.get(`${API_BASE}/knowledge-items/${itemId}`)
    expect(itemResponse.ok()).toBeTruthy()
    expect(await itemResponse.json()).toEqual(
      expect.objectContaining({
        id: itemId,
        sourceType: 'MANUAL_NOTE',
        indexStatus: 'INDEXED',
        knowledgeBaseIds: [kb.id],
      }),
    )

    // ---- N-Q1：新会话检索 Manual Note，来源必须指向该 Item ----
    const conversationId = await createConversationViaUI(page, `Kf-验收-检索个人笔记-${suffix}`)
    await sendMessage(page, 'Kf-番茄工作法-我用多少分钟一个番茄？', '45 分钟')

    const latestAssistant = page.locator('.message.assistant').last()
    await expect(latestAssistant.getByText('已使用个人知识')).toBeVisible()
    await latestAssistant.getByRole('button', { name: /来源/ }).click()
    const sourceItem = latestAssistant.locator('.source-item').filter({ hasText: 'Kf-番茄工作法' })
    await expect(sourceItem.getByText('已引用')).toBeVisible()

    const messagesResponse = await request.get(
      `${API_BASE}/conversations/${conversationId}/messages?limit=20`,
    )
    const messages = ((await messagesResponse.json()).messages ?? []) as MessageView[]
    const assistant = messages.filter((message) => message.role === 'ASSISTANT').at(-1)!
    expect(assistant.ragStatus).toBe('USED')
    expect(assistant.sources).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ itemId, sourceType: 'MANUAL_NOTE', cited: true }),
      ]),
    )
  })

  test('上传 Markdown 文件并被新会话检索到', async ({ page, request }, testInfo) => {
    const suffix = `r${testInfo.retry}`
    await configureModelViaUI(page, `stub-chat-upload-${suffix}`)
    const kb = await createKnowledgeBaseViaUI(page, `Kf-个人知识管理-${suffix}`)

    // ---- 正式 Upload 入口上传 Markdown 文件 ----
    await expect(page.locator('.el-table').first().locator('tbody tr').first()).toBeVisible()
    await page.getByRole('button', { name: '上传文件' }).click()
    const uploadDialog = page.locator('.el-dialog').filter({ hasText: '上传 Markdown / TXT 文件' })
    await uploadDialog.locator('input[type="file"]').setInputFiles({
      name: `kf-dolphin-deploy-${suffix}.md`,
      mimeType: 'text/markdown',
      buffer: Buffer.from(
        '# Kf-海豚-部署手册\n\n' +
          '海豚服务部署三步：1) 备份数据库；2) 导出配置文件；3) 传输原始数据到新主机。\n' +
          '部署后必须运行健康检查脚本 verify.sh。',
        'utf-8',
      ),
    })
    const uploadButton = uploadDialog.getByRole('button', { name: '上传' })
    await expect(uploadButton).toBeEnabled({ timeout: 10_000 })
    await uploadButton.click()

    await page.waitForURL(/knowledge-items\/\d+/)
    const itemId = page.url().split('/').pop()!
    expect(itemId).toBeTruthy()

    // Document Parse 与后续 Knowledge Index 都必须成功，不能只看最终页面。
    const parseTask = await waitForLatestTask(request, 'DOCUMENT_PARSE')
    expect(await waitForTaskTerminal(request, parseTask.id)).toBe('SUCCEEDED')
    const indexTask = await waitForLatestTask(request, 'KNOWLEDGE_INDEX')
    expect(indexTask.businessId).toBe(itemId)
    expect(await waitForTaskTerminal(request, indexTask.id)).toBe('SUCCEEDED')
    await waitForItemIndexed(request, itemId)

    const itemResponse = await request.get(`${API_BASE}/knowledge-items/${itemId}`)
    expect(await itemResponse.json()).toEqual(
      expect.objectContaining({
        id: itemId,
        sourceType: 'UPLOAD_FILE',
        indexStatus: 'INDEXED',
        knowledgeBaseIds: [kb.id],
      }),
    )
    const fileResponse = await request.get(`${API_BASE}/knowledge-items/${itemId}/file`)
    expect(fileResponse.ok()).toBeTruthy()
    const file = await fileResponse.json()
    expect(file.originalFilename).toBe(`kf-dolphin-deploy-${suffix}.md`)
    expect(file.parseStatus).toBe('SUCCEEDED')

    // 详情页展示正式来源、原文件名、解析终态和下载入口。
    await expect(page.getByText('来源：UPLOAD_FILE')).toBeVisible()
    await expect(page.locator('.file-name')).toHaveText(`kf-dolphin-deploy-${suffix}.md`)
    await expect(page.getByText('解析状态：').locator('..')).toContainText('SUCCEEDED')
    await expect(page.getByRole('button', { name: '下载原文件' })).toBeVisible()

    // ---- N-Q2：新会话检索上传 Item，来源必须指向它且保留 cited ----
    const conversationId = await createConversationViaUI(page, `Kf-验收-检索上传笔记-${suffix}`)
    await sendMessage(page, 'Kf-海豚-部署三步是什么？', '备份数据库、导出配置文件、传输原始数据')

    const latestAssistant = page.locator('.message.assistant').last()
    await expect(latestAssistant.getByText('已使用个人知识')).toBeVisible()
    await latestAssistant.getByRole('button', { name: /来源/ }).click()
    const sourceItem = latestAssistant
      .locator('.source-item')
      .filter({ hasText: 'Kf-海豚-部署手册' })
    await expect(sourceItem.getByText('已引用')).toBeVisible()

    const messagesResponse = await request.get(
      `${API_BASE}/conversations/${conversationId}/messages?limit=20`,
    )
    const messages = ((await messagesResponse.json()).messages ?? []) as MessageView[]
    const assistant = messages.filter((message) => message.role === 'ASSISTANT').at(-1)!
    expect(assistant.ragStatus).toBe('USED')
    expect(assistant.sources).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ itemId, sourceType: 'UPLOAD_FILE', cited: true }),
      ]),
    )
  })
})
