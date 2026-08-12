<script setup lang="ts">
/* global AbortController, crypto, localStorage, HTMLElement, KeyboardEvent */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConversation,
  deleteConversation,
  getConversation,
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
import { errorText, networkErrorMessage } from '../utils/errorText'
import ChatMessageItem from '../components/ChatMessageItem.vue'
import { filterConversations, groupConversationsByDate } from '../utils/chat-workspace'

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
const chatBody = ref<HTMLElement | null>(null)
let activeController: AbortController | null = null

const HISTORY_PREF_KEY = 'knowflow.chat.history-panel'
const historyOpen = ref(true)
const historyTransitioning = ref(false)
const isNarrow = ref<boolean | null>(null)
let historyTransitionTimer: number | undefined

/** 新对话占位标题（与后端 TITLE_PLACEHOLDER 一致，见 ADR 0004）。 */
const TITLE_PLACEHOLDER = '新对话'
/** 空白态虚拟会话 id（不会出现在后端会话列表中）。 */
const NEW_CHAT_ID = '__new_chat__'
/** 是否处于「新对话」空白态：未发送首条消息时点 ➕ 不新建，发送首条消息才真正创建会话。 */
const isNewChat = ref(false)
let titlePollTimer: number | undefined

// ---- 双栏工作台：会话列表搜索/分组 ----
const searchKeyword = ref('')

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
  // 空白态：返回一个仅用于渲染的虚拟「新对话」会话；发首条消息后 isNewChat 关闭并真正创建会话
  if (isNewChat.value) {
    return {
      id: NEW_CHAT_ID,
      title: TITLE_PLACEHOLDER,
    } as ConversationResponse
  }
  return conversations.value.find((c) => c.id === activeId.value) ?? null
})

/** 可开始新一轮发送：idle / completed / failed 均可，仅连接或生成进行中禁止。 */
const canStartSend = computed(
  () => stream.phase.value !== 'connecting' && stream.phase.value !== 'streaming',
)

const canSend = computed(
  () => !!activeConversation.value && canStartSend.value && input.value.trim().length > 0,
)

const sendLabel = computed(() =>
  stream.phase.value === 'connecting'
    ? '连接中…'
    : stream.phase.value === 'streaming'
      ? '停止'
      : '发送',
)

const isSendButtonDisabled = computed(() => stream.phase.value !== 'streaming' && !canSend.value)

onMounted(async () => {
  syncHistoryPanelForViewport()
  window.addEventListener('resize', syncHistoryPanelForViewport)
  window.addEventListener('keydown', handleHistoryEscape)
  // 首屏即进入「新对话」草稿态：主区直接展示输入框，发送首条消息时才真正创建会话（ADR 0004 懒创建）。
  enterNewChat()
  await Promise.all([loadConversations(), loadModels()])
})

onUnmounted(() => {
  window.removeEventListener('resize', syncHistoryPanelForViewport)
  window.removeEventListener('keydown', handleHistoryEscape)
  activeController?.abort()
  // 离开页面时终止当前生成：token 守卫关闭，旧流事件不再污染后续会话
  stream.stopGeneration()
  stopExtractionPolling()
  stopTitlePolling()
  if (historyTransitionTimer) window.clearTimeout(historyTransitionTimer)
})

function syncHistoryPanelForViewport() {
  const narrow = window.innerWidth <= 900
  if (narrow === isNarrow.value) return
  isNarrow.value = narrow
  historyOpen.value = narrow ? false : localStorage.getItem(HISTORY_PREF_KEY) !== 'collapsed'
}

function setHistoryOpen(open: boolean) {
  if (historyTransitioning.value || historyOpen.value === open) return
  historyTransitioning.value = true
  historyOpen.value = open
  if (!isNarrow.value) {
    localStorage.setItem(HISTORY_PREF_KEY, open ? 'expanded' : 'collapsed')
  }
  if (historyTransitionTimer) window.clearTimeout(historyTransitionTimer)
  historyTransitionTimer = window.setTimeout(() => {
    historyTransitioning.value = false
    historyTransitionTimer = undefined
  }, 280)
}

function toggleHistory() {
  setHistoryOpen(!historyOpen.value)
}

function handleHistoryEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && historyOpen.value) {
    setHistoryOpen(false)
  }
}

async function loadConversations() {
  try {
    conversations.value = await listConversations()
  } catch {
    // 保留旧数据避免会话列表"消失"，仅提示；首次加载失败保持空列表
    if (conversations.value.length > 0) {
      ElMessage.error('会话列表刷新失败')
    }
  }
}

async function loadModels() {
  try {
    models.value = (await listModelConfigs()).filter((m) => m.enabled)
  } catch {
    if (models.value.length > 0) {
      ElMessage.error('模型列表刷新失败')
    }
  }
}

async function selectConversation(id: string) {
  // 切换会话前终止当前生成，防止旧流对账/占位消息污染新会话
  if (activeId.value !== id) {
    activeController?.abort()
    stream.stopGeneration()
  }
  leaveNewChat()
  activeId.value = id
  const conv = activeConversation.value
  selectedModelId.value = conv?.defaultModelConfigId ?? undefined
  messages.value = []
  nextBefore.value = null
  hasMoreHistory.value = false
  await loadHistory(null)
  startTitlePolling(id)
  if (isNarrow.value === true) setHistoryOpen(false)
}

async function loadHistory(before: string | null) {
  if (!activeId.value) return
  const body = chatBody.value
  const previousScrollHeight = before && body ? body.scrollHeight : 0
  const previousScrollTop = before && body ? body.scrollTop : 0
  let loaded = false
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
    loaded = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载历史失败')
  } finally {
    loadingHistory.value = false
  }
  if (loaded) {
    await nextTick()
    if (body) {
      if (before) {
        body.scrollTop = previousScrollTop + body.scrollHeight - previousScrollHeight
      } else {
        body.scrollTop = body.scrollHeight
      }
    }
  }
}

async function handleCreate() {
  // 状态机语义（ADR 0004）：空白态再点 ➕ 不新建（isNewChat 已为 true 则停留）；进入空白态不落库
  if (!isNewChat.value) {
    enterNewChat()
  }
}

/** 进入「新对话」空白态：主区切换到空白聊天，侧栏高亮占位项；不创建后端会话。 */
function enterNewChat() {
  activeController?.abort()
  stream.stopGeneration()
  stopExtractionPolling()
  activeId.value = null
  messages.value = []
  nextBefore.value = null
  // 空白态无历史可加载，隐藏「加载更早的消息」
  hasMoreHistory.value = false
  input.value = ''
  selectedModelId.value = undefined
  isNewChat.value = true
}

/** 退出空白态：清空占位项，停掉标题轮询。 */
function leaveNewChat() {
  isNewChat.value = false
  stopTitlePolling()
}

/** 空白态发送首条消息：先真正创建会话，再切换到真实会话继续发送。 */
async function createConversationFromNewChat(content: string) {
  const requestedModelId = selectedModelId.value
  let created: ConversationResponse
  try {
    created = await createConversation({})
  } catch (e) {
    // 创建失败：留在空白态并提示，输入内容保留，避免状态卡死
    ElMessage.error(e instanceof Error ? e.message : '创建会话失败')
    return
  }
  conversations.value.unshift(created)
  activeId.value = created.id!
  isNewChat.value = false
  await selectConversation(created.id!)
  // 新会话默认模型：未选时取第一个可用模型（先 selectConversation 避免其把模型覆盖为 undefined）
  if (requestedModelId) {
    selectedModelId.value = requestedModelId
  } else if (!selectedModelId.value && models.value.length > 0) {
    selectedModelId.value = models.value[0].id
  }
  // 用真实会话内容重发刚输入的这条消息（保留 clientMessageId 幂等）
  await resendAsActive(content)
}

/** 用当前真实会话重发 content（用于空白态创建会话后的首次发送）。 */
async function resendAsActive(content: string) {
  const conv = activeConversation.value
  if (!conv?.id || !canStartSend.value) return
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
    conv.id,
    { clientMessageId, content, modelConfigId },
    makeOnEvent(dispatch),
    (err) => {
      stream.stopGeneration()
      stream.streamError.value = networkErrorMessage(err, '发送失败')
      void loadHistory(null)
    },
  )
}

/** 轮询当前会话标题：仅当仍是占位「新对话」时启动，AI 生成/改名后即停止。 */
function startTitlePolling(conversationId: string) {
  stopTitlePolling()
  const conv = conversations.value.find((c) => c.id === conversationId)
  if (!conv || conv.title !== TITLE_PLACEHOLDER) {
    return
  }
  titlePollTimer = setInterval(async () => {
    try {
      const current = conversations.value.find((c) => c.id === conversationId)
      if (!current) {
        stopTitlePolling()
        return
      }
      const fresh = await getConversation(conversationId)
      if (fresh.title !== current.title) {
        current.title = fresh.title
      }
      if (fresh.title !== TITLE_PLACEHOLDER) {
        stopTitlePolling()
      }
    } catch {
      // 轮询失败静默，下轮重试
    }
  }, 3000)
}

function stopTitlePolling() {
  if (titlePollTimer) {
    clearInterval(titlePollTimer)
    titlePollTimer = undefined
  }
}

async function handleExtract() {
  if (!activeConversation.value || isNewChat.value || extracting.value) return
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
  if (!activeConversation.value || isNewChat.value) return
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
  if (!activeConversation.value || isNewChat.value) return
  const conversationId = activeConversation.value.id!
  try {
    await ElMessageBox.confirm(
      '删除后这段对话将不再显示；已经沉淀的知识不会被删除。',
      '删除这段对话？',
      {
        type: 'warning',
        customClass: 'kf-delete-confirm',
        confirmButtonText: '删除会话',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      },
    )
    activeController?.abort()
    stream.stopGeneration()
    await stopGeneration(conversationId)
    await waitForGenerationRelease(conversationId)
    await deleteConversation(conversationId)
    conversations.value = conversations.value.filter((c) => c.id !== conversationId)
    enterNewChat()
    ElMessage.success('会话已删除')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(errorText(e, '删除会话失败，请稍后重试'))
  }
}

/** 停止接口的取消收尾在生成线程执行；短暂等待数据库 active slot 释放后再删除。 */
async function waitForGenerationRelease(conversationId: string): Promise<void> {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    const current = await getConversation(conversationId)
    if (!current.activeGenerationMessageId) return
    await new Promise((resolve) => window.setTimeout(resolve, 120))
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
  // 空白态首条消息：先创建真实会话，再以该会话发送（ADR 0004 懒创建语义）
  if (isNewChat.value) {
    if (!content) return
    void createConversationFromNewChat(content)
    return
  }
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
      stream.streamError.value = networkErrorMessage(err, '发送失败')
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
      stream.streamError.value = networkErrorMessage(err, '重新生成失败')
      void loadHistory(null)
    },
  )
}

watch(selectedModelId, async (newId, oldId) => {
  if (newId && newId !== oldId && activeConversation.value && !isNewChat.value) {
    await updateConversation(activeConversation.value.id!, {
      defaultModelConfigId: newId,
    })
  }
})
</script>

<template>
  <div class="chat-workspace" :class="{ 'history-is-open': historyOpen }">
    <aside
      id="chat-history-panel"
      class="chat-sessions"
      :class="{ open: historyOpen }"
      :aria-hidden="!historyOpen"
    >
      <div class="chat-side-top">
        <button class="chat-plus" aria-label="新建对话" title="新建对话" @click="handleCreate">
          ＋
        </button>
        <div class="side-title-wrap">
          <div class="eyebrow">AI 对话 / 学习入口</div>
          <h2>把问题聊透</h2>
        </div>
        <button
          class="history-close"
          :disabled="historyTransitioning"
          aria-label="收起历史会话"
          @click="setHistoryOpen(false)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <rect x="3" y="4" width="18" height="16" rx="3" stroke-width="2" />
            <path d="M9 4v16" stroke-width="2" />
          </svg>
        </button>
      </div>
      <label class="chat-search">
        <span>⌕</span>
        <input v-model="searchKeyword" placeholder="找一段旧对话…" />
      </label>
      <div class="conv-list">
        <button
          v-if="isNewChat"
          type="button"
          class="session-item"
          :class="{ active: isNewChat }"
          aria-current="true"
          @click="enterNewChat"
        >
          <span class="session-dot" />
          <b>{{ TITLE_PLACEHOLDER }}</b>
          <small>{{ TITLE_PLACEHOLDER }}</small>
          <em>·</em>
        </button>
        <template v-for="group in conversationGroups" :key="group.label">
          <div class="session-label">{{ group.label }}</div>
          <button
            v-for="conv in group.items"
            :key="conv.id"
            type="button"
            class="session-item"
            :class="{ active: conv.id === activeId }"
            :aria-current="conv.id === activeId ? 'true' : undefined"
            @click="selectConversation(conv.id!)"
          >
            <span class="session-dot" />
            <b>{{ conv.title }}</b>
            <small>{{ conv.title }}</small>
            <em>·</em>
          </button>
        </template>
        <div v-if="visibleConversations.length === 0 && !isNewChat" class="conv-empty">
          {{ conversations.length === 0 ? '还没有会话' : '没有匹配的会话' }}
        </div>
      </div>
    </aside>

    <main class="chat-main" :data-conversation-id="isNewChat ? undefined : activeConversation?.id">
      <template v-if="activeConversation">
        <header class="chat-core-head">
          <div class="chat-head-leading">
            <button
              class="history-launcher"
              type="button"
              :disabled="historyTransitioning"
              :aria-hidden="historyOpen"
              :tabindex="historyOpen ? -1 : 0"
              :aria-expanded="historyOpen"
              aria-controls="chat-history-panel"
              aria-label="展开历史会话"
              title="展开历史会话"
              @click="toggleHistory"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                <rect x="3" y="4" width="18" height="16" rx="3" stroke-width="2" />
                <path d="M9 4v16" stroke-width="2" />
              </svg>
            </button>
            <div class="chat-title-row">
              <span
                class="live-ring"
                :class="{ streaming: stream.phase.value !== 'idle' }"
                aria-hidden="true"
              />
              <h2>{{ activeConversation.title }}</h2>
              <span class="sr-only" aria-live="polite">
                {{ stream.phase.value === 'streaming' ? '正在生成回答' : '' }}
              </span>
            </div>
          </div>
          <div class="chat-head-actions">
            <el-select
              v-model="selectedModelId"
              placeholder="选择模型"
              size="small"
              style="width: 170px"
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
            <span class="action-divider" aria-hidden="true" />
            <el-button class="delete-action" size="small" text type="danger" @click="handleDelete">
              删除
            </el-button>
          </div>
          <div v-if="extractionTask" class="header-extraction-hint">
            提取任务 #{{ extractionTask.id }}：状态 {{ extractionTask.status }}，截止消息
            {{ extractionTask.cutoffMessageId }}，输入 {{ extractionTask.inputCharCount }} 字符
          </div>
        </header>

        <div ref="chatBody" class="chat-body" aria-live="polite">
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
          <div class="composebox">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              :autosize="{ minRows: 2, maxRows: 6 }"
              resize="none"
              aria-label="输入消息"
              placeholder="聊聊你正在理解的事，让有价值的内容沉淀下来…"
              @keydown.enter.exact.prevent="handleSend"
            />
            <button
              class="send-btn"
              :class="{ stopping: stream.phase.value === 'streaming' }"
              :disabled="isSendButtonDisabled"
              :aria-label="sendLabel"
              :title="sendLabel"
              @click="stream.phase.value === 'streaming' ? handleStop() : handleSend()"
            >
              <svg
                v-if="stream.phase.value !== 'streaming'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path
                  d="M12 19V5M5 12l7-7 7 7"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <span v-else class="stop-square" aria-hidden="true" />
            </button>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.chat-workspace {
  --history-panel-w: 300px;
  position: relative;
  display: grid;
  grid-template-columns: 0 minmax(0, 1fr);
  height: calc(100vh - var(--kf-mast-h));
  background: var(--kf-paper);
  overflow: hidden;
  transition: grid-template-columns 260ms cubic-bezier(0.22, 1, 0.36, 1);
}
.chat-workspace.history-is-open {
  grid-template-columns: var(--history-panel-w) minmax(0, 1fr);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* ---- Codex 式可收起会话栏：关闭后主区回收全部宽度，只保留入口按钮 ---- */
.history-launcher {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  display: grid;
  place-items: center;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  min-height: 40px;
  padding: 0;
  border: 1px solid var(--kf-ink);
  border-radius: 13px;
  background: var(--kf-white);
  color: var(--kf-ink);
  box-shadow: 2px 2px 0 var(--kf-red);
  cursor: pointer;
  opacity: 1;
  transform: translateX(0) scale(1);
  overflow: hidden;
  transition:
    flex-basis 260ms cubic-bezier(0.22, 1, 0.36, 1),
    width 260ms cubic-bezier(0.22, 1, 0.36, 1),
    border-width 160ms ease,
    opacity 160ms ease,
    transform 280ms cubic-bezier(0.22, 1.4, 0.36, 1);
}
.history-is-open .history-launcher {
  pointer-events: none;
  flex-basis: 0;
  width: 0;
  border-width: 0;
  opacity: 0;
  transform: translateX(-16px) scale(0.82);
}
.history-is-open .chat-head-leading {
  gap: 0;
}
.history-launcher svg,
.history-close svg {
  width: 19px;
  height: 19px;
}
.history-launcher:focus-visible {
  outline: 2px solid var(--kf-red);
  outline-offset: 3px;
}
.history-launcher:disabled {
  cursor: default;
}
.history-close {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--kf-ink);
  border-radius: 12px;
  background: var(--kf-white);
  color: var(--kf-ink);
  cursor: pointer;
  transition:
    transform 180ms var(--kf-ease),
    box-shadow 180ms var(--kf-ease);
}
.history-close:disabled {
  cursor: default;
}
.history-launcher:hover,
.history-close:hover {
  transform: translateY(-2px);
  box-shadow: 2px 2px 0 var(--kf-red);
}
.chat-sessions {
  position: relative;
  grid-column: 1;
  z-index: 22;
  box-sizing: border-box;
  width: var(--history-panel-w);
  border-right: 1px solid var(--kf-line);
  padding: 16px 14px 18px;
  background: var(--kf-paper-3);
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow-y: auto;
  box-shadow: 10px 0 26px rgba(25, 24, 21, 0.1);
  transform: translateX(-100%);
  visibility: hidden;
  transition:
    transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
    visibility 0s linear 260ms;
}
.chat-sessions.open {
  transform: translateX(0);
  visibility: visible;
  transition:
    transform 260ms cubic-bezier(0.22, 1, 0.36, 1),
    visibility 0s;
}
.chat-side-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 0 2px 14px;
}
.side-title-wrap {
  min-width: 0;
  flex: 1;
}
.chat-side-top h2 {
  font-size: 17px;
  letter-spacing: -1.2px;
  line-height: 1.1;
  margin: 7px 0 0;
  font-weight: 900;
  white-space: nowrap;
}
.eyebrow {
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--kf-hot);
  font-weight: 900;
}
.chat-plus {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border: 1px solid var(--kf-ink);
  background: var(--kf-red);
  border-radius: 12px;
  font-size: 20px;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 3px 3px 0 var(--kf-ink);
  color: var(--kf-white);
  transition:
    transform 180ms var(--kf-ease),
    box-shadow 180ms var(--kf-ease);
}
.chat-plus:hover {
  transform: translateY(-2px) rotate(4deg);
  box-shadow: 5px 5px 0 var(--kf-ink);
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
  padding: 0 5px 5px 0;
}
.session-label {
  font-size: 9px;
  color: var(--kf-muted);
  letter-spacing: 0.1em;
  font-weight: 900;
  padding: 8px 7px;
}
.session-item {
  box-sizing: border-box;
  position: relative;
  border: 2px solid var(--kf-ink);
  background: var(--kf-white);
  width: calc(100% - 4px);
  text-align: left;
  border-radius: 999px;
  padding: 10px 34px 10px 26px;
  margin: 0 0 8px;
  cursor: pointer;
  transition: 0.2s;
  font: inherit;
  color: inherit;
  min-height: var(--kf-touch-min);
}
.session-item:hover {
  background: var(--kf-paper-2);
}
.session-item:focus-visible {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
.session-item.active {
  background: var(--kf-white);
  box-shadow: 4px 4px 0 var(--kf-red);
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
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 8px;
  font-style: normal;
  color: var(--kf-muted);
  font-weight: 900;
}
.session-dot {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
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
  grid-column: 2;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--kf-white);
}
.chat-core-head {
  box-sizing: border-box;
  flex: 0 0 64px;
  height: 64px;
  min-height: 64px;
  padding: 6px 16px;
  border-bottom: 1px solid var(--kf-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: rgba(255, 253, 248, 0.92);
  position: sticky;
  top: 0;
  z-index: 8;
  backdrop-filter: blur(12px);
  flex-wrap: nowrap;
}
.chat-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1 1 auto;
}
.chat-head-leading {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
  transition: gap 260ms cubic-bezier(0.22, 1, 0.36, 1);
}
.chat-title-row h2 {
  min-width: 0;
  max-width: 100%;
  font-size: 15px;
  margin: 0;
  font-weight: 900;
  letter-spacing: -0.4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.live-ring {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--kf-hot);
  box-shadow: 0 0 0 4px var(--kf-hot-soft);
  flex: 0 0 auto;
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
  gap: 7px;
  align-items: center;
  flex-wrap: wrap;
  flex: 0 0 auto;
  padding: 4px;
  border: 1px solid var(--kf-line);
  background: var(--kf-white);
  border-radius: 16px;
  box-shadow: 2px 2px 0 rgba(25, 24, 21, 0.08);
}
.chat-head-actions :deep(.el-button) {
  border-radius: 12px;
  min-height: 40px;
}
.chat-head-actions :deep(.model-select .el-select__wrapper) {
  min-height: 40px;
  border-radius: 12px;
  background: var(--kf-paper);
}
.chat-head-actions :deep(.model-select) {
  min-width: 0;
}
.action-divider {
  width: 1px;
  height: 28px;
  margin-left: 2px;
  background: var(--kf-line);
}
.delete-action {
  padding-inline: 12px !important;
}
.header-extraction-hint {
  position: absolute;
  left: 16px;
  bottom: 2px;
  width: calc(100% - 32px);
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
.chat-input {
  padding: 10px 16px 14px;
  border-top: 1px solid var(--kf-line);
}
.composebox {
  position: relative;
  display: flex;
  align-items: flex-end;
  box-sizing: border-box;
  border: 1.5px solid var(--kf-ink);
  background: var(--kf-white);
  border-radius: 12px;
  min-height: 88px;
  overflow: hidden;
  transition:
    box-shadow var(--kf-duration-fast) var(--kf-ease),
    border-color var(--kf-duration-fast) var(--kf-ease);
}
.composebox:focus-within {
  border-color: var(--kf-green);
  box-shadow: 0 0 0 2px var(--kf-green-soft);
}
.composebox :deep(.el-textarea) {
  flex: 1;
  min-width: 0;
  padding: 20px 72px 20px 20px;
  border-radius: inherit;
  overflow: hidden;
}
.composebox :deep(.el-textarea__inner) {
  box-sizing: border-box;
  font-family: inherit;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  padding: 0;
  margin: 0;
  border: 0;
  border-radius: inherit;
  box-shadow: none;
  background: transparent;
  resize: none;
  overflow-y: auto;
  color: var(--kf-ink);
  caret-color: var(--kf-ink);
}
.composebox :deep(.el-textarea__inner::placeholder) {
  color: var(--kf-muted);
  font: inherit;
  line-height: 24px;
  opacity: 0.72;
}
.composebox :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}
.send-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 44px;
  height: 44px;
  min-height: 44px;
  border-radius: 12px;
  border: 1px solid var(--kf-ink);
  background: var(--kf-ink);
  color: var(--kf-paper);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition:
    transform var(--kf-duration-fast) var(--kf-ease),
    background var(--kf-duration-fast) var(--kf-ease);
}
.send-btn:hover:not(:disabled) {
  background: var(--kf-green);
  transform: translateY(-2px);
}
.send-btn.stopping {
  background: var(--kf-hot);
  border-color: var(--kf-hot);
}
.send-btn.stopping:hover:not(:disabled) {
  background: var(--kf-ink);
}
.send-btn:focus-visible {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
.send-btn:disabled {
  background: var(--kf-line);
  border-color: var(--kf-line);
  cursor: not-allowed;
  color: var(--kf-muted);
}
.send-btn svg {
  width: 18px;
  height: 18px;
}
.stop-square {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: currentColor;
}

/* ---- 响应式收窄：桌面主 + 窄屏基础适配 ---- */
@media (max-width: 900px) {
  .chat-workspace.history-is-open {
    grid-template-columns: 0 minmax(0, 1fr);
  }
  .chat-sessions {
    position: absolute;
    inset: 0 auto 0 0;
    width: min(var(--history-panel-w), calc(100vw - 16px));
  }
  .chat-core-head {
    height: auto;
    min-height: 64px;
    flex: 0 0 auto;
    flex-wrap: wrap;
  }
  .chat-head-leading {
    flex: 1 1 auto;
  }
  .chat-head-actions {
    flex: 1 0 100%;
    width: 100%;
    margin-top: 4px;
  }
}
</style>

<style>
/* teleport 到 body 的会话删除确认框：与产品纸感语言一致，并保持中文动作层级。 */
.kf-delete-confirm.el-message-box {
  width: min(440px, calc(100vw - 32px));
  padding: 24px;
  border: 1px solid var(--kf-ink);
  border-radius: 22px;
  background: var(--kf-white);
  box-shadow: 8px 8px 0 var(--kf-red);
}
.kf-delete-confirm .el-message-box__title {
  color: var(--kf-ink);
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.04em;
}
.kf-delete-confirm .el-message-box__content {
  padding: 18px 0 20px;
  color: var(--kf-muted);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.75;
}
.kf-delete-confirm .el-message-box__status {
  color: var(--kf-red);
}
.kf-delete-confirm .el-message-box__btns {
  gap: 10px;
  padding: 0;
}
.kf-delete-confirm .el-message-box__btns .el-button {
  min-width: 112px;
  margin: 0;
  border-radius: 14px;
}
.kf-delete-confirm .el-message-box__btns .el-button--primary {
  border-color: var(--kf-red);
  background: var(--kf-red);
  color: var(--kf-white);
  box-shadow: 3px 3px 0 var(--kf-ink);
}
</style>
