import { expect, test } from '@playwright/test'
import {
  API_BASE,
  cleanupAll,
  configureModelViaUI,
  createKnowledgeBase,
  createKnowledgeBaseViaUI,
  enterNewConversationViaUI,
  openKnowledgeBaseDetailViaUI,
  readActiveConversationId,
  sendMessage,
  waitForItemIndexed,
  waitForLatestTask,
  waitForTaskTerminal,
} from './helpers'

interface MessageView {
  role?: string
  ragStatus?: string
  sources?: Array<{
    documentId?: string
    documentTitle?: string
    sourceType?: string
    cited?: boolean
  }>
}

/**
 * 闭环 2：个人笔记/上传闭环 E2E。
 *
 * 通过正式前端完成：创建 KB → 库详情页新建 Manual Note 或上传 Markdown → 形成 KnowledgeDocument
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

    // ---- 库详情页新建 Manual Note ----
    await openKnowledgeBaseDetailViaUI(page, kb.name)
    await page.getByRole('button', { name: '新建笔记' }).first().click()
    const noteDialog = page.locator('.el-dialog').filter({ hasText: '新建笔记' })
    await noteDialog
      .locator('input[placeholder="留空则取正文首行"]')
      .fill(`Kf-番茄工作法-个人实践-${suffix}`)
    await noteDialog
      .locator('textarea[placeholder="Markdown 正文"]')
      .fill('我实践番茄工作法时固定使用 45 分钟工作 + 10 分钟休息。')
    await noteDialog.getByRole('button', { name: '创建' }).click()

    await page.waitForURL(/documents\/\d+/)
    const itemId = page.url().split('/').pop()!
    expect(itemId).toBeTruthy()
    await expect(page.getByText('来源：MANUAL_NOTE')).toBeVisible()

    await waitForItemIndexed(request, itemId)
    const itemResponse = await request.get(`${API_BASE}/documents/${itemId}`)
    expect(itemResponse.ok()).toBeTruthy()
    expect(await itemResponse.json()).toEqual(
      expect.objectContaining({
        id: itemId,
        sourceType: 'MANUAL_NOTE',
        indexStatus: 'INDEXED',
        knowledgeBaseId: kb.id,
      }),
    )

    // 额外准备一个启用但暂无文档的库，验证会话可持久化多库范围；空库因无已索引文档不会进入 Router 目录（该约束由
    // RouterService 单测确定性覆盖），此处断言检索来源仍只命中 Manual Note 所在库、rag_status=USED。
    const emptyKb = await createKnowledgeBase(request, `Kf-空知识库-${suffix}`)

    // ---- N-Q1：新会话手动绑定两个库，再检索 Manual Note ----
    await enterNewConversationViaUI(page)
    await page.locator('.binding-trigger').click()
    const bindingEditor = page.locator('.binding-editor')
    await expect(bindingEditor).toBeVisible()
    await bindingEditor.getByText(kb.name, { exact: true }).click()
    await bindingEditor.getByText(emptyKb.name, { exact: true }).click()
    await bindingEditor.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.binding-trigger')).toContainText('2 个知识库')
    await sendMessage(page, 'Kf-番茄工作法-我用多少分钟一个番茄？', '45 分钟')
    const conversationId = await readActiveConversationId(page)
    expect(conversationId).toBeTruthy()

    const conversationResponse = await request.get(`${API_BASE}/conversations/${conversationId}`)
    expect(await conversationResponse.json()).toEqual(
      expect.objectContaining({ knowledgeBaseIds: [kb.id, emptyKb.id].sort() }),
    )

    const latestAssistant = page.locator('.message.assistant').last()
    await expect(latestAssistant.getByRole('button', { name: /来源个人知识库/ })).toBeVisible()
    await latestAssistant.getByRole('button', { name: /来源/ }).click()
    const sourceItem = latestAssistant.locator('.source-item').filter({ hasText: 'Kf-番茄工作法' })
    await expect(sourceItem.getByText('已引用')).toBeVisible()

    // ---- P4 引用增强（G35）：来源面板显示所属知识库名 ----
    await expect(sourceItem.locator('.source-kb-link')).toHaveText(kb.name)

    // ---- P4 引用增强（G35）：正文 [S1] 悬停预览与点击高亮 ----
    const cite = latestAssistant.locator('.kf-cite').first()
    await expect(cite).toBeVisible()
    await cite.hover()
    const popper = page.locator('.el-popper').filter({ hasText: 'Kf-番茄工作法' }).last()
    await expect(popper).toBeVisible()
    await expect(popper).toContainText('番茄工作法')
    await cite.click()
    await expect(
      latestAssistant.locator('.source-item.highlighted').filter({ hasText: 'Kf-番茄工作法' }),
    ).toBeVisible()

    const messagesResponse = await request.get(
      `${API_BASE}/conversations/${conversationId}/messages?limit=20`,
    )
    const messages = ((await messagesResponse.json()).messages ?? []) as MessageView[]
    const assistant = messages.filter((message) => message.role === 'ASSISTANT').at(-1)!
    expect(assistant.ragStatus).toBe('USED')
    expect(assistant.sources).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ documentId: itemId, sourceType: 'MANUAL_NOTE', cited: true }),
      ]),
    )

    // 显式清空绑定恢复 AUTO；不发送新的消息也能从 API 验证持久化语义。
    await page.locator('.binding-trigger').click()
    await page.locator('.binding-editor').getByRole('button', { name: '切回自动选择' }).click()
    await page.locator('.binding-editor').getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.binding-trigger')).toContainText('自动选择')
    const autoConversation = await request.get(`${API_BASE}/conversations/${conversationId}`)
    expect(await autoConversation.json()).toEqual(expect.objectContaining({ knowledgeBaseIds: [] }))
  })

  test('上传 Markdown 文件并被新会话检索到', async ({ page, request }, testInfo) => {
    const suffix = `r${testInfo.retry}`
    await configureModelViaUI(page, `stub-chat-upload-${suffix}`)
    const kb = await createKnowledgeBaseViaUI(page, `Kf-个人知识管理-${suffix}`)

    // ---- 库详情页上传入口上传 Markdown 文件 ----
    await openKnowledgeBaseDetailViaUI(page, kb.name)
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

    await page.waitForURL(/documents\/\d+/)
    const itemId = page.url().split('/').pop()!
    expect(itemId).toBeTruthy()

    // Document Parse 与后续 Knowledge Index 都必须成功，不能只看最终页面。
    const parseTask = await waitForLatestTask(request, 'DOCUMENT_PARSE')
    expect(await waitForTaskTerminal(request, parseTask.id)).toBe('SUCCEEDED')
    const indexTask = await waitForLatestTask(request, 'KNOWLEDGE_INDEX')
    expect(indexTask.businessId).toBe(itemId)
    expect(await waitForTaskTerminal(request, indexTask.id)).toBe('SUCCEEDED')
    await waitForItemIndexed(request, itemId)

    const itemResponse = await request.get(`${API_BASE}/documents/${itemId}`)
    expect(await itemResponse.json()).toEqual(
      expect.objectContaining({
        id: itemId,
        sourceType: 'UPLOAD_FILE',
        indexStatus: 'INDEXED',
        knowledgeBaseId: kb.id,
      }),
    )
    const fileResponse = await request.get(`${API_BASE}/documents/${itemId}/file`)
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
    await enterNewConversationViaUI(page)
    await sendMessage(page, 'Kf-海豚-部署三步是什么？', '备份数据库、导出配置文件、传输原始数据')
    const conversationId = await readActiveConversationId(page)
    expect(conversationId).toBeTruthy()

    const latestAssistant = page.locator('.message.assistant').last()
    await expect(latestAssistant.getByRole('button', { name: /来源个人知识库/ })).toBeVisible()
    await latestAssistant.getByRole('button', { name: /来源/ }).click()
    const sourceItem = latestAssistant
      .locator('.source-item')
      .filter({ hasText: 'Kf-海豚-部署手册' })
    await expect(sourceItem.getByText('已引用')).toBeVisible()

    // ---- P4 引用增强（G35）：来源面板显示所属知识库名 ----
    await expect(sourceItem.locator('.source-kb-link')).toHaveText(kb.name)

    const messagesResponse = await request.get(
      `${API_BASE}/conversations/${conversationId}/messages?limit=20`,
    )
    const messages = ((await messagesResponse.json()).messages ?? []) as MessageView[]
    const assistant = messages.filter((message) => message.role === 'ASSISTANT').at(-1)!
    expect(assistant.ragStatus).toBe('USED')
    expect(assistant.sources).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ documentId: itemId, sourceType: 'UPLOAD_FILE', cited: true }),
      ]),
    )
  })
})
