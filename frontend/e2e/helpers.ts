import { expect, type APIRequestContext, type Page } from '@playwright/test'

/** E2E 共享工具：通过后端 API 做数据准备，等待异步任务/索引终态。 */

export const BACKEND_BASE = 'http://127.0.0.1:18081'
export const API_BASE = `${BACKEND_BASE}/api/v1`

export interface PreparedKB {
  id: string
  name: string
}

export interface PreparedModel {
  id: string
  name: string
}

export interface ProcessingTaskView {
  id: string
  taskType: string
  status: string
  businessId?: string
  retryOfTaskId?: string
  retryCount?: number
  maxRetries?: number
  failureCode?: string
  lastError?: string
}

/** 创建知识库（名称唯一，带 Kf- 前缀）。 */
export async function createKnowledgeBase(
  request: APIRequestContext,
  name: string,
): Promise<PreparedKB> {
  const res = await request.post(`${API_BASE}/knowledge-bases`, {
    data: { name, description: 'E2E 固定验收知识库' },
  })
  expect(res.status(), `create KB ${name}`).toBe(201)
  const body = await res.json()
  return { id: body.id, name: body.name }
}

/** 创建指向本地模型 stub 的 ModelConfig（chat/utility 共用）并通过 Utility 能力测试。 */
export async function createModelConfig(
  request: APIRequestContext,
  displayName: string,
): Promise<PreparedModel> {
  const res = await request.post(`${API_BASE}/model-configs`, {
    data: {
      displayName,
      providerName: 'stub',
      baseUrl: 'http://127.0.0.1:18082/v1',
      modelName: 'stub-model',
      temperature: 0.7,
      maxOutputTokens: 1024,
      apiKey: 'test-key',
      enabled: true,
    },
  })
  expect(res.status(), `create model config ${displayName}`).toBe(201)
  const body = await res.json()
  const configId = body.id as string

  // Utility 能力测试：stub 对 Router/Candidate schema 都返回合法 JSON，测试应通过
  const cap = await request.post(`${API_BASE}/model-configs/${configId}/test-utility-capability`)
  expect(cap.status(), 'utility capability test').toBe(200)
  const capBody = await cap.json()
  expect(capBody.success, `utility capability should pass: ${JSON.stringify(capBody)}`).toBe(true)

  return { id: configId, name: body.displayName }
}

/** 设置 Owner AI Settings 默认 ChatModel 与 Utility Model（同一 stub config）。 */
export async function setOwnerDefaults(
  request: APIRequestContext,
  modelConfigId: string,
): Promise<void> {
  const res = await request.put(`${API_BASE}/owner-ai-settings`, {
    data: {
      defaultChatModelConfigId: modelConfigId,
      utilityModelConfigId: modelConfigId,
    },
  })
  expect(res.status(), 'set owner ai settings').toBe(200)
}

/** 通过正式 Knowledge 页面创建知识库；API 只用于读取创建后的稳定字符串 ID。 */
export async function createKnowledgeBaseViaUI(page: Page, name: string): Promise<PreparedKB> {
  await page.goto('/knowledge-bases')
  await page.getByRole('button', { name: '新建知识库', exact: true }).first().click()
  const dialog = page.getByRole('dialog').filter({ hasText: '新建知识库' })
  await dialog.getByRole('textbox', { name: /名称/ }).fill(name)
  await dialog.getByRole('textbox', { name: '描述' }).fill('E2E 固定验收知识库')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.locator('.el-table__row').filter({ hasText: name }).first()).toBeVisible()

  const id = await page.evaluate(async (expectedName) => {
    const res = await fetch('/api/v1/knowledge-bases')
    const items = await res.json()
    return items.find((item: { name?: string }) => item.name === expectedName)?.id ?? ''
  }, name)
  expect(id, `created KB id for ${name}`).toBeTruthy()
  return { id, name }
}

/**
 * 通过正式 Settings 页面创建并验证模型，然后设置为默认 Chat 与 Utility。
 * API 只读取最终 ID 和 Owner settings，用于稳定断言。
 */
export async function configureModelViaUI(page: Page, displayName: string): Promise<PreparedModel> {
  await page.goto('/model-settings')
  await page.getByRole('button', { name: '新建模型配置' }).click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '新建模型配置' })
  await dialog.locator('input[placeholder="例如 DeepSeek Chat"]').fill(displayName)
  await dialog.locator('input[placeholder="例如 DeepSeek"]').fill('stub')
  await dialog
    .locator('input[placeholder="https://api.deepseek.com"]')
    .fill('http://127.0.0.1:18082/v1')
  await dialog.locator('input[placeholder="deepseek-chat"]').fill('stub-model')
  await dialog.locator('input[type="password"]').fill('test-key')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  const card = page
    .getByRole('region', { name: '模型配置列表' })
    .getByRole('article')
    .filter({ has: page.getByRole('heading', { name: displayName, exact: true }) })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '测试连接' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '连接测试通过' })).toBeVisible({
    timeout: 30_000,
  })
  await card.getByRole('button', { name: '测试 Utility' }).click()
  await expect(page.locator('.el-message').filter({ hasText: 'Utility 能力测试通过' })).toBeVisible(
    {
      timeout: 30_000,
    },
  )
  // 先设 Utility：当 Owner settings 为空时，「设为默认」会把同一配置同时回填为
  // Utility，随后 Utility 按钮按产品规则禁用，E2E 再点击会永久等待。
  await card.getByRole('button', { name: '设为 Utility' }).click()
  await expect(card.getByText('Utility', { exact: true })).toBeVisible()
  await card.getByRole('button', { name: '设为默认' }).click()
  await expect(card.getByText('默认 Chat', { exact: true })).toBeVisible()

  const result = await page.evaluate(async (expectedName) => {
    const [configsRes, settingsRes] = await Promise.all([
      fetch('/api/v1/model-configs'),
      fetch('/api/v1/owner-ai-settings'),
    ])
    const configs = await configsRes.json()
    const settings = await settingsRes.json()
    const config = configs.find(
      (item: { displayName?: string }) => item.displayName === expectedName,
    )
    return { id: config?.id ?? '', settings }
  }, displayName)
  expect(result.id, `created model id for ${displayName}`).toBeTruthy()
  expect(result.settings.defaultChatModelConfigId).toBe(result.id)
  expect(result.settings.utilityModelConfigId).toBe(result.id)
  return { id: result.id, name: displayName }
}

/** 等待一个 ProcessingTask 进入终态 SUCCEEDED/FAILED，最多 timeoutMs。 */
export async function waitForTaskTerminal(
  request: APIRequestContext,
  taskId: string,
  timeoutMs = 60_000,
): Promise<'SUCCEEDED' | 'FAILED'> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const res = await request.get(`${API_BASE}/processing-tasks`)
    expect(res.ok()).toBeTruthy()
    const tasks = await res.json()
    const task = tasks.find((t: { id: string }) => t.id === taskId)
    if (task && (task.status === 'SUCCEEDED' || task.status === 'FAILED')) {
      return task.status
    }
    await new Promise((r) => setTimeout(r, 1000))
  }
  throw new Error(`task ${taskId} did not reach terminal within ${timeoutMs}ms`)
}

/** 等待最新的指定类型任务出现；列表由后端按创建时间倒序返回。 */
export async function waitForLatestTask(
  request: APIRequestContext,
  taskType: string,
  timeoutMs = 30_000,
): Promise<ProcessingTaskView> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const res = await request.get(`${API_BASE}/processing-tasks`)
    expect(res.ok()).toBeTruthy()
    const tasks = (await res.json()) as ProcessingTaskView[]
    const task = tasks.find((candidate) => candidate.taskType === taskType)
    if (task) return task
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`task type ${taskType} did not appear within ${timeoutMs}ms`)
}

/** 等待指定 taskType + businessId 的任务出现，避免误取此前同类型任务。 */
export async function waitForTaskForBusiness(
  request: APIRequestContext,
  taskType: string,
  businessId: string,
  timeoutMs = 30_000,
): Promise<ProcessingTaskView> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const res = await request.get(`${API_BASE}/processing-tasks`)
    expect(res.ok()).toBeTruthy()
    const tasks = (await res.json()) as ProcessingTaskView[]
    const task = tasks.find(
      (candidate) => candidate.taskType === taskType && candidate.businessId === businessId,
    )
    if (task) return task
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(
    `task type ${taskType} for business ${businessId} did not appear within ${timeoutMs}ms`,
  )
}

/** 读取指定 ProcessingTask 的当前视图。 */
export async function getProcessingTask(
  request: APIRequestContext,
  taskId: string,
): Promise<ProcessingTaskView> {
  const res = await request.get(`${API_BASE}/processing-tasks`)
  expect(res.ok()).toBeTruthy()
  const task = ((await res.json()) as ProcessingTaskView[]).find(
    (candidate) => candidate.id === taskId,
  )
  expect(task, `processing task ${taskId}`).toBeTruthy()
  return task!
}

/** 等待一个 KnowledgeItem 索引成功（indexStatus=INDEXED）。 */
export async function waitForItemIndexed(
  request: APIRequestContext,
  itemId: string,
  timeoutMs = 60_000,
): Promise<void> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const res = await request.get(`${API_BASE}/documents/${itemId}`)
    expect(res.ok()).toBeTruthy()
    const item = await res.json()
    if (item.indexStatus === 'INDEXED') return
    if (item.indexStatus === 'FAILED') throw new Error(`item ${itemId} indexed FAILED`)
    await new Promise((r) => setTimeout(r, 1000))
  }
  throw new Error(`item ${itemId} not INDEXED within ${timeoutMs}ms`)
}

/** 从库列表页进入库详情：点击库名（P2 拆分后文档入口都在库详情页）。 */
export async function openKnowledgeBaseDetailViaUI(page: Page, kbName: string): Promise<void> {
  await page.goto('/knowledge-bases')
  await page.getByRole('table').getByText(kbName, { exact: true }).click()
  await expect(page.getByRole('heading', { name: kbName })).toBeVisible()
  await expect(page.getByRole('button', { name: '上传文件' })).toBeVisible()
}

/** 在 Chat 页面进入「新对话」草稿态；首条消息发送后才会真正创建后端会话。 */
export async function enterNewConversationViaUI(page: Page): Promise<void> {
  await page.goto('/chat')
  await page.getByRole('button', { name: '新建对话' }).click()
  await expect(page.locator('.chat-core-head h2')).toHaveText('新对话')
  await expect(page.locator('.chat-input textarea')).toBeEnabled()
}

/** 从 Chat 主区读取当前选中的真实会话 id，避免依赖列表时间排序猜测当前项。 */
export async function readActiveConversationId(page: Page): Promise<string> {
  await expect(page.locator('.chat-main')).toHaveAttribute('data-conversation-id', /^\d+$/)
  return (await page.locator('.chat-main').getAttribute('data-conversation-id')) ?? ''
}

/** 发送一条聊天消息并等待 assistant 回复完成（期望文本出现且流式结束）。 */
export async function sendMessage(
  page: Page,
  content: string,
  expectedText: string,
): Promise<void> {
  await page.locator('.chat-input textarea').fill(content)
  // fill 使 canSend 为 true（按钮从 disabled 变 enabled），再点击
  await expect(page.getByRole('button', { name: '发送' })).toBeEnabled({ timeout: 10_000 })
  await page.getByRole('button', { name: '发送' }).click()
  // 等期望文本出现在任意 assistant 正文中（覆盖 reconcile 重排导致的索引变化）
  await expect
    .poll(
      () =>
        page
          .locator('.message.assistant .msg-content')
          .allTextContents()
          .then((texts) => texts.some((t) => t.includes(expectedText))),
      { timeout: 30_000 },
    )
    .toBe(true)
  // 等流式结束：GENERATING 的「停止」按钮消失（reconcile 后 generationStatus=COMPLETED）
  await expect(
    page.locator('.message.assistant .msg-actions .el-button', { hasText: '停止' }),
  ).toHaveCount(0, { timeout: 30_000 })
  // completed 已代表后端事实源提交和 active slot 释放；不使用隐藏 API 为 UI 路径增加额外门禁。
  await expect(page.locator('.chat-input textarea')).toBeEnabled({ timeout: 10_000 })
  await expect(page.getByRole('button', { name: '发送' })).toBeDisabled()
}

/**
 * 验证当前后端明确运行在 e2e profile。cleanupAll 在此检查通过前绝不发送删除请求，
 * 避免误连开发实例时清除用户本地数据。
 */
export async function assertE2EGuard(request: APIRequestContext): Promise<void> {
  const res = await request.get(`${BACKEND_BASE}/actuator/info`)
  expect(res.ok(), 'E2E backend must expose guarded actuator info').toBeTruthy()
  const info = await res.json()
  expect(info?.knowflow?.e2e, 'Refusing cleanup: backend is not running with e2e profile').toBe(
    true,
  )
}

/** 清理隔离测试数据：先删会话，再删 Item（解除 KB 归属），最后删 KB。 */
export async function cleanupAll(request: APIRequestContext): Promise<void> {
  await assertE2EGuard(request)
  const convs = await request.get(`${API_BASE}/conversations`)
  if (convs.ok()) {
    for (const c of await convs.json()) {
      await request.delete(`${API_BASE}/conversations/${c.id}`)
    }
  }
  const items = await request.get(`${API_BASE}/documents`)
  if (items.ok()) {
    const pageBody = await items.json()
    for (const it of (pageBody.items ?? []) as Array<{ id: string; rowVersion?: number }>) {
      await request.delete(`${API_BASE}/documents/${it.id}`, {
        headers: { 'If-Match': `"${it.rowVersion ?? 0}"` },
      })
    }
  }
  const kbs = await request.get(`${API_BASE}/knowledge-bases`)
  if (kbs.ok()) {
    for (const kb of await kbs.json()) {
      await request.delete(`${API_BASE}/knowledge-bases/${kb.id}`, {
        headers: { 'If-Match': `"${kb.rowVersion ?? 0}"` },
      })
    }
  }
}
