<script setup lang="ts">
/* global AbortController, crypto */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConversation,
  deleteConversation,
  listConversations,
  listMessages,
  streamRegenerate,
  streamSend,
  stopGeneration,
  updateConversation,
} from '../api/conversations'
import { listModelConfigs } from '../api/model-configs'
import { listProcessingTasks } from '../api/processing-tasks'
import { triggerExtraction } from '../api/extraction'
import type { ExtractionTaskResponse } from '../api/extraction'
import type { ConversationResponse, MessageResponse } from '../api/types/conversation'
import type { ModelConfigResponse } from '../api/types/model-config'
import { useChatStream } from '../composables/useChatStream'
import type { RouterDiagnostic } from '../composables/useChatStream'
import ChatMessageItem from '../components/ChatMessageItem.vue'
import RouterTracePanel from '../components/RouterTracePanel.vue'
import ChatMemoryPanel from '../components/ChatMemoryPanel.vue'
import KnowledgeDomainsPanel from '../components/KnowledgeDomainsPanel.vue'
import { filterConversations, groupConversationsByDate } from '../utils/chat-workspace'
import { listKnowledgeBases } from '../api/knowledge-bases'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'

const conversations = ref<ConversationResponse[]>([])
const activeId = ref<string | null>(null)
const messages = ref<MessageResponse[]>([])
/** 按 assistant 消息 id 缓存 SSE sources.available 携带的 Router 诊断（仅实时可见，历史不回放）。 */
const routerByMessage = ref<Record<string, RouterDiagnostic>>({})
const models = ref<ModelConfigResponse[]>([])
const input = ref('')
const selectedModelId = ref<string | undefined>()
const loadingHistory = ref(false)
const nextBefore = ref<string | null>(null)
const hasMoreHistory = ref(true)
let activeController: AbortController | null = null

// ---- 三栏工作台：会话列表搜索/分组 + Inspector 知识域 ----
const searchKeyword = ref('')
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const selectedDomainIds = ref<string[]>([])

const visibleConversations = computed(() =>
  filterConversations(conversations.value, searchKeyword.value),
)
const conversationGroups = computed(() => groupConversationsByDate(visibleConversations.value))

const stream = useChatStream({
  getConversation: () => activeConversation.value,
  getMessages: () => messages.value,
  setMessages: (m) => (messages.value = m),
  // 流结束后对账：重新拉取服务器最终状态，确保失败/取消/断连后 UI 与 DB 一致
  reconcile: () => {
    void loadHistory(null)
  },
})

function messageRouter(msg: MessageResponse): RouterDiagnostic | undefined {
  return msg.id ? routerByMessage.value[msg.id] : undefined
}

const extracting = ref(false)
const extractionTask = ref<ExtractionTaskResponse | null>(null)
let extractionPollTimer: number | undefined

const hasCompletedMessages = computed(() =>
  messages.value.some((m) => m.role === 'ASSISTANT' && m.generationStatus === 'COMPLETED'),
)

const canExtract = computed(
  () => !!activeConversation.value && canStartSend.value && hasCompletedMessages.value,
)

const activeConversation = computed(() => {
  return conversations.value.find((c) => c.id === activeId.value) ?? null
})

/** Inspector 路由轨迹：取当前消息流最后一条 assistant 的诊断。 */
const latestAssistant = computed(() => {
  const assistants = messages.value.filter((m) => m.role === 'ASSISTANT')
  return assistants[assistants.length - 1] ?? null
})
const latestAssistantId = computed(() => latestAssistant.value?.id ?? null)
const latestAssistantRagStatus = computed(() => latestAssistant.value?.ragStatus ?? null)

/** 可开始新一轮发送：idle / completed / failed 均可，仅连接或生成进行中禁止。 */
const canStartSend = computed(
  () => stream.phase.value !== 'connecting' && stream.phase.value !== 'streaming',
)

const canSend = computed(
  () => !!activeConversation.value && canStartSend.value && input.value.trim().length > 0,
)

onMounted(async () => {
  await Promise.all([loadConversations(), loadModels()])
})

onUnmounted(() => {
  activeController?.abort()
  // 离开页面时终止当前生成：token 守卫关闭，旧流事件不再污染后续会话
  stream.stopGeneration()
  stopExtractionPolling()
})

async function loadConversations() {
  try {
    conversations.value = await listConversations()
  } catch {
    conversations.value = []
  }
}

async function loadModels() {
  try {
    models.value = (await listModelConfigs()).filter((m) => m.enabled)
  } catch {
    models.value = []
  }
}

async function loadKnowledgeBases() {
  try {
    knowledgeBases.value = (await listKnowledgeBases()).filter((kb) => kb.enabled)
  } catch {
    knowledgeBases.value = []
  }
}

function toggleDomain(id: string) {
  selectedDomainIds.value = selectedDomainIds.value.includes(id)
    ? selectedDomainIds.value.filter((x) => x !== id)
    : [...selectedDomainIds.value, id]
}

async function selectConversation(id: string) {
  // 切换会话前终止当前生成，防止旧流对账/占位消息污染新会话
  if (activeId.value !== id) {
    activeController?.abort()
    stream.stopGeneration()
  }
  activeId.value = id
  const conv = activeConversation.value
  selectedModelId.value = conv?.defaultModelConfigId ?? undefined
  messages.value = []
  nextBefore.value = null
  hasMoreHistory.value = true
  // Inspector 需要知识域，仅在首次进入会话时加载（避免每次进页拉全量）
  if (knowledgeBases.value.length === 0) {
    void loadKnowledgeBases()
  }
  await loadHistory(null)
}

async function loadHistory(before: string | null) {
  if (!activeId.value) return
  loadingHistory.value = true
  try {
    const page = await listMessages(activeId.value, {
      before: before ?? undefined,
      limit: 20,
    })
    const list = page.messages ?? []
    if (before) {
      messages.value = [...list, ...messages.value]
    } else {
      messages.value = list
    }
    nextBefore.value = page.nextBefore ?? null
    hasMoreHistory.value = !!page.nextBefore
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载历史失败')
  } finally {
    loadingHistory.value = false
  }
}

async function handleCreate() {
  try {
    const result = await ElMessageBox.prompt('输入会话标题', '新建会话', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
    })
    const title = result.value.trim()
    if (!title) {
      ElMessage.warning('标题不能为空')
      return
    }
    const created = await createConversation({ title })
    conversations.value.unshift(created)
    await selectConversation(created.id!)
  } catch {
    // 用户取消
  }
}

async function handleExtract() {
  if (!activeConversation.value || extracting.value) return
  extracting.value = true
  extractionTask.value = null
  stopExtractionPolling()
  try {
    extractionTask.value = await triggerExtraction(activeConversation.value.id!)
    ElMessage.success(
      `已触发提取（截止消息 ${extractionTask.value.cutoffMessageId}），结果可稍后在「待审核」查看`,
    )
    startExtractionPolling(extractionTask.value.processingTaskId)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '提取失败'
    ElMessage.error(msg)
  } finally {
    extracting.value = false
  }
}

/** 触发后轮询提取任务的终态（SUCCEEDED/FAILED），SUCCEEDED 且 0 候选时明确提示。 */
function startExtractionPolling(processingTaskId?: string) {
  stopExtractionPolling()
  if (!processingTaskId) return
  extractionPollTimer = setInterval(async () => {
    if (!extractionTask.value) return
    try {
      const tasks = await listProcessingTasks()
      const current = tasks.find((t) => t.id === processingTaskId)
      if (!current) return
      extractionTask.value = { ...extractionTask.value, status: current.status }
      if (current.status === 'SUCCEEDED' || current.status === 'FAILED') {
        stopExtractionPolling()
        if (current.status === 'SUCCEEDED' && (extractionTask.value.candidateCount ?? 0) === 0) {
          ElMessage.info('提取完成：未发现值得沉淀的内容（0 个候选）')
        } else if (current.status === 'FAILED') {
          ElMessage.error(`提取失败：${current.failureCode ?? current.lastError ?? '未知错误'}`)
        }
      }
    } catch {
      // 轮询失败静默，下轮重试
    }
  }, 3000)
}

function stopExtractionPolling() {
  if (extractionPollTimer) {
    clearInterval(extractionPollTimer)
    extractionPollTimer = undefined
  }
}

async function handleRename() {
  if (!activeConversation.value) return
  try {
    const result = await ElMessageBox.prompt('输入新标题', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: activeConversation.value.title,
    })
    const title = result.value.trim()
    if (!title) return
    const updated = await updateConversation(activeConversation.value.id!, { title })
    const idx = conversations.value.findIndex((c) => c.id === updated.id)
    if (idx >= 0) conversations.value[idx] = updated
  } catch {
    // 用户取消
  }
}

async function handleDelete() {
  if (!activeConversation.value) return
  try {
    await ElMessageBox.confirm('删除会话？此操作不可恢复。', '确认删除', { type: 'warning' })
    await deleteConversation(activeConversation.value.id!)
    conversations.value = conversations.value.filter((c) => c.id !== activeConversation.value!.id)
    activeId.value = null
    messages.value = []
  } catch {
    // 用户取消或删除被拒
  }
}

/** 包装 startGeneration 的 dispatch：先缓存 Router 诊断（供 route-badge/Inspector），再交给 token 守卫。 */
function makeOnEvent(dispatch: (eventName: string, data: unknown) => void) {
  return (eventName: string, data: unknown) => {
    if (eventName === 'sources.available') {
      const d = data as { assistantMessageId?: string; router?: RouterDiagnostic }
      if (d.assistantMessageId && d.router) {
        routerByMessage.value = {
          ...routerByMessage.value,
          [d.assistantMessageId]: d.router,
        }
      }
    }
    dispatch(eventName, data)
  }
}

function handleSend() {
  const content = input.value.trim()
  const conv = activeConversation.value
  // 同步守卫：连接/生成进行中（connecting/streaming）拒绝再次发送，堵住重复点击窗口
  if (!content || !conv || !canStartSend.value) return

  const dispatch = stream.startGeneration()
  input.value = ''
  const clientMessageId = crypto.randomUUID()
  messages.value = [
    ...messages.value,
    {
      id: `local-${clientMessageId}`,
      conversationId: conv.id,
      role: 'USER',
      content,
      active: false,
    },
  ]

  const modelConfigId = selectedModelId.value || undefined

  activeController = streamSend(
    conv.id!,
    { clientMessageId, content, modelConfigId },
    makeOnEvent(dispatch),
    (err) => {
      // 连接级错误（fetch 失败/断连）：终止状态机，避免消息永久 loading
      stream.stopGeneration()
      stream.streamError.value = err instanceof Error ? err.message : '发送失败'
      // 断连/错误后对账最终状态
      void loadHistory(null)
    },
  )
}

async function handleStop() {
  if (!activeConversation.value) return
  try {
    // 先确认后端已设置取消标志，再断开当前 SSE；否则断连可能先被服务端归类为 FAILED。
    await stopGeneration(activeConversation.value.id!)
    stream.stopGeneration()
    activeController?.abort()
    await loadHistory(null)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '停止生成失败')
  }
}

function handleRegenerate(message: MessageResponse) {
  if (!activeConversation.value || !message.id) return
  const dispatch = stream.startGeneration()
  activeController = streamRegenerate(
    activeConversation.value.id!,
    { modelConfigId: selectedModelId.value || undefined },
    makeOnEvent(dispatch),
    (err) => {
      stream.stopGeneration()
      stream.streamError.value = err instanceof Error ? err.message : '重新生成失败'
      void loadHistory(null)
    },
  )
}

watch(selectedModelId, async (newId, oldId) => {
  if (newId && newId !== oldId && activeConversation.value) {
    await updateConversation(activeConversation.value.id!, {
      defaultModelConfigId: newId,
    })
  }
})
</script>

<template>
  <div class="chat-workspace">
    <aside class="chat-sessions">
      <div class="chat-side-top">
        <div>
          <div class="eyebrow">AI 对话 / 学习入口</div>
          <h2>把问题聊透。</h2>
        </div>
        <button class="chat-plus" aria-label="新建对话" @click="handleCreate">＋</button>
      </div>
      <label class="chat-search">
        <span>⌕</span>
        <input v-model="searchKeyword" placeholder="找一段旧对话…" />
      </label>
      <div class="conv-list">
        <template v-for="group in conversationGroups" :key="group.label">
          <div class="session-label">{{ group.label }}</div>
          <div
            v-for="conv in group.items"
            :key="conv.id"
            class="session-item"
            :class="{ active: conv.id === activeId }"
            @click="selectConversation(conv.id!)"
          >
            <span class="session-dot" />
            <b>{{ conv.title }}</b>
            <small>{{ conv.title }}</small>
            <em>·</em>
          </div>
        </template>
        <div v-if="visibleConversations.length === 0" class="conv-empty">
          {{ conversations.length === 0 ? '还没有会话' : '没有匹配的会话' }}
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <template v-if="activeConversation">
        <header class="chat-core-head">
          <div class="chat-title-row">
            <span class="live-ring" :class="{ streaming: stream.phase.value !== 'idle' }" />
            <h2>{{ activeConversation.title }}</h2>
          </div>
          <div class="chat-head-actions">
            <el-select
              v-model="selectedModelId"
              placeholder="选择模型"
              size="small"
              style="width: 180px"
              class="model-select"
            >
              <el-option v-for="m in models" :key="m.id" :label="m.displayName" :value="m.id" />
            </el-select>
            <el-button size="small" @click="handleRename">重命名</el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="!canExtract"
              :loading="extracting"
              @click="handleExtract"
            >
              提取知识
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete">删除</el-button>
          </div>
          <div v-if="extractionTask" class="header-extraction-hint">
            提取任务 #{{ extractionTask.id }}：状态 {{ extractionTask.status }}，截止消息
            {{ extractionTask.cutoffMessageId }}，输入 {{ extractionTask.inputCharCount }} 字符
          </div>
        </header>

        <div class="chat-body">
          <div v-if="loadingHistory" class="history-loading">加载中…</div>
          <el-button
            v-if="hasMoreHistory && !loadingHistory"
            class="load-more"
            size="small"
            text
            @click="loadHistory(nextBefore)"
          >
            加载更早的消息
          </el-button>

          <ChatMessageItem
            v-for="msg in messages"
            :key="msg.id"
            :msg="msg"
            :router="messageRouter(msg) ?? null"
            @stop="handleStop"
            @regenerate="handleRegenerate"
          />
          <div v-if="stream.streamError.value" class="stream-error">
            {{ stream.streamError.value }}
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="input-actions">
            <el-button type="primary" :disabled="!canSend" @click="handleSend">
              {{
                stream.phase.value === 'connecting'
                  ? '连接中…'
                  : stream.phase.value === 'streaming'
                    ? '生成中…'
                    : '发送'
              }}
            </el-button>
          </div>
        </div>
      </template>

      <div v-else class="chat-empty">
        <p>选择一个会话或新建会话开始聊天</p>
      </div>
    </main>

    <aside v-if="activeConversation" class="chat-inspector">
      <RouterTracePanel
        :router="routerByMessage[latestAssistantId ?? '']"
        :rag-status="latestAssistantRagStatus"
      />
      <ChatMemoryPanel :messages="messages" />
      <KnowledgeDomainsPanel
        :domains="knowledgeBases"
        :selected-ids="selectedDomainIds"
        @toggle="toggleDomain"
      />
    </aside>
  </div>
</template>

<style scoped>
.chat-workspace {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr) 318px;
  height: calc(100vh - var(--kf-mast-h));
  background: var(--kf-paper);
}

/* ---- 左栏：会话列表 ---- */
.chat-sessions {
  border-right: 1px solid var(--kf-line);
  padding: 28px 16px 22px;
  background: var(--kf-paper-3);
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
}
.chat-side-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 0 5px 18px;
}
.chat-side-top h2 {
  font-size: 27px;
  letter-spacing: -1.6px;
  line-height: 1.05;
  margin: 7px 0 0;
  font-weight: 900;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--kf-hot);
  font-weight: 900;
}
.chat-plus {
  width: 38px;
  height: 38px;
  border: 1px solid var(--kf-ink);
  background: var(--kf-yellow);
  border-radius: 12px;
  font-size: 20px;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--kf-ink);
  color: var(--kf-ink);
}
.chat-search {
  height: 42px;
  border: 1px solid var(--kf-line);
  background: var(--kf-white);
  border-radius: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  margin: 0 1px 20px;
}
.chat-search span {
  font-size: 18px;
}
.chat-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--kf-ink);
  font-size: 11px;
  font-weight: 700;
}
.chat-search input::placeholder {
  color: #9a9286;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
}
.session-label {
  font-size: 9px;
  color: var(--kf-muted);
  letter-spacing: 0.1em;
  font-weight: 900;
  padding: 8px 7px;
}
.session-item {
  position: relative;
  border: 1px solid transparent;
  background: transparent;
  width: 100%;
  text-align: left;
  border-radius: 15px;
  padding: 12px 38px 12px 28px;
  margin-bottom: 4px;
  cursor: pointer;
  transition: 0.2s;
}
.session-item:hover {
  background: rgba(255, 253, 248, 0.7);
}
.session-item.active {
  background: var(--kf-white);
  border-color: var(--kf-ink);
  box-shadow: 3px 3px 0 var(--kf-ink);
}
.session-item b {
  display: block;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item small {
  display: block;
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 700;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item em {
  position: absolute;
  right: 11px;
  top: 12px;
  font-size: 8px;
  font-style: normal;
  color: var(--kf-muted);
  font-weight: 900;
}
.session-dot {
  position: absolute;
  left: 11px;
  top: 16px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--kf-hot);
  border: 1px solid var(--kf-ink);
}
.conv-empty {
  color: #9a9286;
  text-align: center;
  padding: 20px 0;
  font-size: 11px;
  font-weight: 700;
}

/* ---- 中栏：聊天核心 ---- */
.chat-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--kf-white);
}
.chat-core-head {
  min-height: 84px;
  padding: 17px 24px;
  border-bottom: 1px solid var(--kf-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: rgba(255, 253, 248, 0.92);
  position: sticky;
  top: var(--kf-mast-h);
  z-index: 8;
  backdrop-filter: blur(12px);
  flex-wrap: wrap;
}
.chat-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.chat-title-row h2 {
  font-size: 17px;
  margin: 0;
  font-weight: 900;
  letter-spacing: -0.5px;
}
.live-ring {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--kf-hot);
  box-shadow: 0 0 0 5px var(--kf-hot-soft);
}
.live-ring.streaming {
  animation: pulse 1s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
.chat-head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.header-extraction-hint {
  width: 100%;
  font-size: 9px;
  color: var(--kf-muted);
  font-weight: 700;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.stream-error {
  color: #f56c6c;
  margin-top: 8px;
}
.history-loading {
  color: #999;
  text-align: center;
  padding: 8px;
}
.load-more {
  margin-bottom: 8px;
}
.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}
.chat-input {
  padding: 12px 16px;
  border-top: 1px solid #eee;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* ---- 右栏：Inspector ---- */
.chat-inspector {
  border-left: 1px solid var(--kf-line);
  background: var(--kf-paper);
  padding: 24px 18px;
  overflow-y: auto;
}
</style>
