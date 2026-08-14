<script setup lang="ts">
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
import { listModelConfigs, getOwnerAiSettings } from '../api/model-configs'
import { listKnowledgeBases } from '../api/knowledge-bases'
import { listProcessingTasks } from '../api/processing-tasks'
import { triggerExtraction } from '../api/extraction'
import type { ExtractionTaskResponse } from '../api/extraction'
import type { ConversationResponse, MessageResponse } from '../api/types/conversation'
import type { ModelConfigResponse, OwnerAiSettingsResponse } from '../api/types/model-config'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'
import { useChatStream } from '../composables/useChatStream'
import type { RouterDiagnostic } from '../composables/useChatStream'
import { useChatGenerationStore } from '../stores/chatGeneration'
import { errorText, networkErrorMessage } from '../utils/errorText'
import ChatMessageItem from '../components/ChatMessageItem.vue'
import { filterConversations, groupConversationsByDate } from '../utils/chat-workspace'

const generationStore = useChatGenerationStore()

const conversations = ref<ConversationResponse[]>([])
const activeId = ref<string | null>(null)
const messages = ref<MessageResponse[]>([])
/** 按 assistant 消息 id 缓存 SSE sources.available 携带的 Router 诊断（仅实时可见，历史不回放）。 */
const routerByMessage = ref<Record<string, RouterDiagnostic>>({})
const models = ref<ModelConfigResponse[]>([])
const ownerSettings = ref<OwnerAiSettingsResponse | null>(null)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const knowledgeBasesLoaded = ref(false)
const knowledgeBasesLoading = ref(false)
const draftKnowledgeBaseIds = ref<string[]>([])
const bindingPopoverOpen = ref(false)
const bindingSaving = ref(false)
const input = ref('')
const selectedModelId = ref<string | undefined>()
const loadingHistory = ref(false)
const nextBefore = ref<string | null>(null)
const hasMoreHistory = ref(true)
const chatBody = ref<HTMLElement | null>(null)

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
      knowledgeBaseIds: draftKnowledgeBaseIds.value,
    } as ConversationResponse
  }
  return conversations.value.find((c) => c.id === activeId.value) ?? null
})

const activeKnowledgeBaseIds = computed(() =>
  isNewChat.value
    ? draftKnowledgeBaseIds.value
    : (activeConversation.value?.knowledgeBaseIds ?? []),
)

const knowledgeBaseById = computed(() => new Map(knowledgeBases.value.map((kb) => [kb.id!, kb])))

/** Composer 胶囊优先展示可识别的当前范围，完整详情仍由绑定弹层承接。 */
const bindingLabel = computed(() => {
  const ids = activeKnowledgeBaseIds.value
  if (ids.length === 0) return '自动选择'
  if (ids.length === 1) return knowledgeBaseById.value.get(ids[0])?.name ?? '1 个知识库'
  return `${ids.length} 个知识库`
})

const selectableKnowledgeBases = computed(() =>
  knowledgeBases.value.filter((kb) => kb.enabled && kb.id),
)

const unavailableBindingIds = computed(() =>
  activeKnowledgeBaseIds.value.filter((id) => !knowledgeBaseById.value.get(id)?.enabled),
)

/** 编辑弹层草稿中的失效绑定（已停用/已删除），与保存动作一致，避免编辑中与已保存状态不一致。 */
const draftUnavailableBindingIds = computed(() =>
  draftKnowledgeBaseIds.value.filter((id) => !knowledgeBaseById.value.get(id)?.enabled),
)

/** Owner 设置的默认聊天模型 id（无则 undefined）。 */
const defaultChatModelId = computed(() => ownerSettings.value?.defaultChatModelConfigId)

/** Owner 设置的 Utility 模型 id（无则 undefined）。 */
const utilityModelId = computed(() => ownerSettings.value?.utilityModelConfigId)

/** 默认聊天模型的展示名，供选择器标注「跟随默认」。 */
const defaultModelName = computed(() => {
  const id = defaultChatModelId.value
  if (!id) return undefined
  return models.value.find((m) => m.id === id)?.displayName
})

/** 未显式选择模型时选择器的占位文案：明确告知实际生效模型是默认模型。 */
const modelSelectPlaceholder = computed(() =>
  defaultModelName.value ? `跟随默认（${defaultModelName.value}）` : '请选择模型',
)

/** 选择器「跟随默认」哨兵值：与真实模型 id 区分，选中它表示不指定模型。 */
const FOLLOW_DEFAULT_MODEL = ''
const pickerModel = computed({
  get: () => selectedModelId.value ?? FOLLOW_DEFAULT_MODEL,
  set: (v: string) => {
    selectedModelId.value = v === FOLLOW_DEFAULT_MODEL ? undefined : v
  },
})

/** 模型选项展示名：默认聊天模型标「默认」，Utility 模型标「Utility」，便于区分角色。 */
function modelRoleLabel(m: ModelConfigResponse): string {
  if (m.id === defaultChatModelId.value) return `${m.displayName}（默认）`
  if (m.id === utilityModelId.value) return `${m.displayName}（Utility）`
  return m.displayName
}

/** 可开始新一轮发送：idle / completed / failed 均可，仅连接或生成进行中禁止。 */
const canStartSend = computed(() => {
  // store 守卫：切走再切回时本组件 phase 已复位，但该会话可能在后台仍有 live 生成
  if (activeId.value && generationStore.isGenerating(activeId.value)) return false
  return stream.phase.value !== 'connecting' && stream.phase.value !== 'streaming'
})

/**
 * 当前会话展示的消息：历史消息 + store 中「进行中」的 live 生成消息（按 messageId 去重合并）。
 * 切回一个仍在后台生成的会话时，loadHistory 只能拉到空内容的 GENERATING 占位，
 * 这里用 store 实时累积的增量内容补齐，回答完成后由 watch 对账回服务端最终状态。
 */
const visibleMessages = computed(() => {
  if (!activeId.value) return messages.value
  const live = generationStore.liveOf(activeId.value)
  if (!live || live.status !== 'generating' || !live.messageId) return messages.value
  const copy = [...messages.value]
  const idx = copy.findIndex((m) => m.id === live.messageId)
  const liveMessage: MessageResponse = {
    id: live.messageId,
    conversationId: activeId.value,
    role: 'ASSISTANT',
    content: live.content,
    generationStatus: 'GENERATING',
    active: false,
  }
  if (idx >= 0) {
    copy[idx] = { ...copy[idx], content: live.content, generationStatus: 'GENERATING' }
  } else {
    copy.push(liveMessage)
  }
  return copy
})

const canSend = computed(
  () => !!activeConversation.value && canStartSend.value && input.value.trim().length > 0,
)

/** 思考期轻提示：仅在"有活跃生成但尚无任何输出气泡"时显示（re-generate 复用既有气泡时不重复提示）。 */
const showThinking = computed(
  () =>
    stream.isThinking.value &&
    !messages.value.some((m) => m.role === 'ASSISTANT' && m.generationStatus === 'GENERATING'),
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
  await Promise.all([loadConversations(), loadModels(), loadOwnerSettings(), loadKnowledgeBases()])
})

onUnmounted(() => {
  window.removeEventListener('resize', syncHistoryPanelForViewport)
  window.removeEventListener('keydown', handleHistoryEscape)
  // 不 abort 当前生成流：流由 store 继续消费，生成在后台完成后落库（后端断连静默模式），
  // 重进会话时从历史拉取完整回答。token 守卫关闭使旧流事件不再污染后续会话 UI。
  stream.stopGeneration()
  stopExtractionPolling()
  stopTitlePolling()
  stopCompletionPolling()
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

async function loadOwnerSettings() {
  try {
    ownerSettings.value = await getOwnerAiSettings()
  } catch {
    ownerSettings.value = null
  }
}

async function loadKnowledgeBases() {
  knowledgeBasesLoading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
    knowledgeBasesLoaded.value = true
  } catch {
    knowledgeBasesLoaded.value = false
  } finally {
    knowledgeBasesLoading.value = false
  }
}

async function selectConversation(id: string) {
  // 切换会话不中断原会话的后台生成（store 继续消费流）；仅使旧流事件对当前 UI 失效。
  if (activeId.value !== id) {
    stream.stopGeneration()
  }
  // 已终态（completed/failed）的 live 条目在服务端已落库，对账后清理，避免残留
  const live = generationStore.liveOf(id)
  if (live && live.status !== 'generating') {
    generationStore.clearGeneration(id)
  }
  leaveNewChat()
  activeId.value = id
  const conv = activeConversation.value
  selectedModelId.value = conv?.defaultModelConfigId ?? undefined
  draftKnowledgeBaseIds.value = [...(conv?.knowledgeBaseIds ?? [])]
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
    // 刷新兜底：拉到的 GENERATING 消息若没有对应 live 流（页面刷新后连接已断），
    // 启动轮询直至生成完成再重拉完整内容（后端非增量落库，期间没有实时增量可看）
    if (!before) maybeStartCompletionPolling()
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
  // 不 abort 当前生成流：原会话的生成继续在后台完成（store 接管），空白态不受影响
  stream.stopGeneration()
  stopExtractionPolling()
  stopCompletionPolling()
  activeId.value = null
  messages.value = []
  nextBefore.value = null
  // 空白态无历史可加载，隐藏「加载更早的消息」
  hasMoreHistory.value = false
  input.value = ''
  selectedModelId.value = undefined
  draftKnowledgeBaseIds.value = []
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
    created = await createConversation({ knowledgeBaseIds: draftKnowledgeBaseIds.value })
  } catch (e) {
    // 创建失败：留在空白态并提示，输入内容保留，避免状态卡死
    ElMessage.error(e instanceof Error ? e.message : '创建会话失败')
    return
  }
  conversations.value.unshift(created)
  activeId.value = created.id!
  isNewChat.value = false
  await selectConversation(created.id!)
  // 新会话默认模型：保留空白态已显式选择的模型；未选择则不携带 modelConfigId，交由后端回退 Owner 默认
  if (requestedModelId) {
    selectedModelId.value = requestedModelId
  }
  // 用真实会话内容重发刚输入的这条消息（保留 clientMessageId 幂等）
  await resendAsActive(content)
}

function openBindingEditor() {
  if (!canStartSend.value) {
    ElMessage.info('回答完成后可修改知识库，修改从下一轮生效')
    return
  }
  if (bindingPopoverOpen.value) {
    bindingPopoverOpen.value = false
    return
  }
  draftKnowledgeBaseIds.value = [...activeKnowledgeBaseIds.value]
  bindingPopoverOpen.value = true
  if (!knowledgeBasesLoaded.value && !knowledgeBasesLoading.value) {
    void loadKnowledgeBases()
  }
}

function clearBindingDraft() {
  draftKnowledgeBaseIds.value = []
}

/** 显式移除某个失效绑定（已停用/已删除），而不是保存时静默清除。 */
function removeDraftBinding(id: string) {
  draftKnowledgeBaseIds.value = draftKnowledgeBaseIds.value.filter((x) => x !== id)
}

async function saveKnowledgeBaseBinding() {
  if (!knowledgeBasesLoaded.value) return
  // 失效绑定保留在草稿中随保存提交（ADR 0009「不自动清理」）；后端对「新增」ID 执行存在/启用校验，对既有失效 ID 放行。
  if (isNewChat.value) {
    bindingPopoverOpen.value = false
    return
  }
  const conv = activeConversation.value
  if (!conv?.id || conv.rowVersion === undefined) return
  bindingSaving.value = true
  try {
    replaceConversation(
      await updateConversation(conv.id, {
        knowledgeBaseIds: draftKnowledgeBaseIds.value,
        rowVersion: conv.rowVersion,
      }),
    )
    bindingPopoverOpen.value = false
    ElMessage.success('知识库范围已保存，将从下一轮问答生效')
  } catch (e) {
    draftKnowledgeBaseIds.value = [...(conv.knowledgeBaseIds ?? [])]
    ElMessage.error(errorText(e, '知识库范围保存失败'))
  } finally {
    bindingSaving.value = false
  }
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
  const conversationId = conv.id
  generationStore.startStream(
    conversationId,
    (onDispatch) =>
      streamSend(
        conversationId,
        { clientMessageId, content, modelConfigId },
        makeOnEvent(onDispatch),
        (err) => {
          stream.stopGeneration()
          stream.streamError.value = networkErrorMessage(err, '发送失败')
          generationStore.clearGeneration(conversationId)
          void loadHistory(null)
        },
      ),
    dispatch,
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

/** 刷新兜底轮询定时器：store 无 live 流（连接已断）但消息仍 GENERATING 时，轮询至终态再重拉。 */
let completionPollTimer: number | undefined

function stopCompletionPolling() {
  if (completionPollTimer) {
    clearInterval(completionPollTimer)
    completionPollTimer = undefined
  }
}

/** 检测「孤儿 GENERATING 消息」（页面刷新后连接已断，无 live 流接管）并启动轮询补齐。 */
function maybeStartCompletionPolling() {
  stopCompletionPolling()
  if (!activeId.value) return
  // 有 live 流时由 store 实时累积内容并随事件对账，不需要轮询
  if (generationStore.isGenerating(activeId.value)) return
  if (!messages.value.some((m) => m.role === 'ASSISTANT' && m.generationStatus === 'GENERATING')) {
    return
  }
  completionPollTimer = window.setInterval(async () => {
    if (!activeId.value) return
    try {
      const page = await listMessages(activeId.value, { limit: 5 })
      const stillGenerating = (page.messages ?? []).some((m) => m.generationStatus === 'GENERATING')
      if (!stillGenerating) {
        stopCompletionPolling()
        await loadHistory(null)
      }
    } catch {
      // 轮询失败静默，下轮重试
    }
  }, 1500)
}

/**
 * live 生成终态对账：流在本组件外部完成（切走期间/切回会话后）时，服务端已落库，
 * 重新拉取完整历史并清理 store 条目；本组件发起并正常完成的流由 handleCompleted/
 * handleFailed 直接对账，这里只清理残留（消息列表已非 GENERATING 则不再重复拉取）。
 */
watch(
  () => (activeId.value ? generationStore.liveOf(activeId.value)?.status : undefined),
  async (status) => {
    if (status !== 'completed' && status !== 'failed' && status !== 'cancelled') return
    if (!activeId.value) return
    generationStore.clearGeneration(activeId.value)
    if (messages.value.some((m) => m.role === 'ASSISTANT' && m.generationStatus === 'GENERATING')) {
      await loadHistory(null)
    }
  },
)

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
    // 删除前先取消后端生成并停掉本地流，避免后台生成对已删除会话写状态
    stream.stopGeneration()
    await stopGeneration(conversationId)
    generationStore.stopStream(conversationId)
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

  generationStore.startStream(
    conv.id!,
    (onDispatch) =>
      streamSend(
        conv.id!,
        { clientMessageId, content, modelConfigId },
        makeOnEvent(onDispatch),
        (err) => {
          // 连接级错误（fetch 失败/断连）：终止状态机，避免消息永久 loading
          stream.stopGeneration()
          stream.streamError.value = networkErrorMessage(err, '发送失败')
          generationStore.clearGeneration(conv.id!)
          // 断连/错误后对账最终状态
          void loadHistory(null)
        },
      ),
    dispatch,
  )
}

async function handleStop() {
  if (!activeConversation.value) return
  const conversationId = activeConversation.value.id!
  try {
    // 先确认后端已设置取消标志，再断开本地流；否则断连可能先被服务端归类为 FAILED。
    await stopGeneration(conversationId)
    stream.stopGeneration()
    generationStore.stopStream(conversationId)
    await loadHistory(null)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '停止生成失败')
  }
}

function handleRegenerate() {
  if (!activeConversation.value || !canStartSend.value) return
  const conversationId = activeConversation.value.id!
  const dispatch = stream.startGeneration()
  // 后端 regenerate 锁定"最新一条 assistant 消息"原位覆盖；前端同步清空该条，旧内容立即消失，流式新内容写回同一位置。
  const target = [...messages.value].reverse().find((m) => m.role === 'ASSISTANT')
  if (target) {
    target.content = ''
    target.generationStatus = 'GENERATING'
    target.errorCode = undefined
    target.ragStatus = undefined
    target.sources = undefined
    target.usage = undefined
  }
  generationStore.startStream(
    conversationId,
    (onDispatch) =>
      streamRegenerate(
        conversationId,
        { modelConfigId: selectedModelId.value || undefined },
        makeOnEvent(onDispatch),
        (err) => {
          stream.stopGeneration()
          stream.streamError.value = networkErrorMessage(err, '重新生成失败')
          generationStore.clearGeneration(conversationId)
          void loadHistory(null)
        },
      ),
    dispatch,
  )
}

watch(selectedModelId, async (newId, oldId) => {
  const conv = activeConversation.value
  if (!conv || isNewChat.value || newId === oldId) return
  try {
    if (newId) {
      replaceConversation(await updateConversation(conv.id!, { defaultModelConfigId: newId }))
    } else if (oldId && conv.defaultModelConfigId) {
      // 显式切回「跟随默认」：清空会话级覆盖，交由后端回退 Owner 默认
      replaceConversation(await updateConversation(conv.id!, { defaultModelConfigId: '' }))
    }
  } catch {
    ElMessage.error('模型切换保存失败')
  }
})

/** 用 PATCH 返回值刷新本地会话，保持 defaultModelConfigId 与后端一致。 */
function replaceConversation(updated: ConversationResponse) {
  const idx = conversations.value.findIndex((c) => c.id === updated.id)
  if (idx >= 0) conversations.value[idx] = updated
}
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
            <span v-if="generationStore.isGenerating(conv.id!)" class="session-generating">
              生成中
            </span>
            <em v-if="!generationStore.isGenerating(conv.id!)">·</em>
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
            v-for="msg in visibleMessages"
            :key="msg.id"
            :msg="msg"
            :router="messageRouter(msg) ?? null"
            @stop="handleStop"
            @regenerate="handleRegenerate"
          />
          <div v-if="showThinking" class="thinking-indicator" aria-live="polite">
            <span class="thinking-dots" aria-hidden="true"><i /><i /><i /></span>
            <span>AI 正在检索并组织回答…</span>
          </div>
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
              placeholder="向 KnowFlow 提问…"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="composer-toolbar">
              <div class="composer-tools">
                <el-popover
                  v-model:visible="bindingPopoverOpen"
                  placement="top-start"
                  :width="340"
                  trigger="manual"
                  :disabled="!canStartSend"
                >
                  <template #reference>
                    <el-button
                      size="small"
                      class="composer-pill binding-trigger"
                      :class="{ 'has-unavailable': unavailableBindingIds.length > 0 }"
                      :disabled="!canStartSend"
                      :aria-label="
                        unavailableBindingIds.length > 0
                          ? `${bindingLabel}，包含不可用知识库`
                          : bindingLabel
                      "
                      @click="openBindingEditor"
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                        <path
                          d="M4 6.5A2.5 2.5 0 0 1 6.5 4H20v15H6.5A2.5 2.5 0 0 1 4 16.5v-10Z"
                          stroke-width="1.8"
                        />
                        <path
                          d="M4 16.5A2.5 2.5 0 0 1 6.5 14H20M8 8h8"
                          stroke-width="1.8"
                          stroke-linecap="round"
                        />
                      </svg>
                      <span class="composer-pill-label">{{ bindingLabel }}</span>
                    </el-button>
                  </template>
                  <div class="binding-editor">
                    <div class="binding-editor-head">
                      <strong>本会话知识库</strong>
                      <el-button size="small" text @click="clearBindingDraft">
                        切回自动选择
                      </el-button>
                    </div>
                    <p>绑定后仅在这些知识库中判断是否需要检索；问题无关时不会强制引用。</p>
                    <el-alert
                      v-if="!knowledgeBasesLoaded"
                      type="warning"
                      :closable="false"
                      title="知识库列表加载失败，请重试"
                    />
                    <el-scrollbar v-else class="binding-options" max-height="220px">
                      <el-checkbox-group v-model="draftKnowledgeBaseIds">
                        <el-checkbox
                          v-for="kb in selectableKnowledgeBases"
                          :key="kb.id"
                          :value="kb.id!"
                        >
                          {{ kb.name }}
                        </el-checkbox>
                      </el-checkbox-group>
                      <el-empty
                        v-if="selectableKnowledgeBases.length === 0"
                        description="暂无可选知识库"
                        :image-size="48"
                      />
                    </el-scrollbar>
                    <div v-if="draftUnavailableBindingIds.length > 0" class="binding-unavailable">
                      <span
                        v-for="id in draftUnavailableBindingIds"
                        :key="id"
                        class="binding-stale-chip"
                      >
                        {{
                          knowledgeBaseById.get(id)
                            ? `${knowledgeBaseById.get(id)?.name}（已停用）`
                            : `不可用知识库 #${id}`
                        }}
                        <button
                          type="button"
                          class="binding-chip-remove"
                          :aria-label="`移除知识库 ${id}`"
                          @click="removeDraftBinding(id)"
                        >
                          ×
                        </button>
                      </span>
                    </div>
                    <div class="binding-editor-actions">
                      <el-button
                        size="small"
                        :loading="knowledgeBasesLoading"
                        @click="loadKnowledgeBases"
                      >
                        重试加载
                      </el-button>
                      <el-button
                        size="small"
                        type="primary"
                        :disabled="!knowledgeBasesLoaded"
                        :loading="bindingSaving"
                        @click="saveKnowledgeBaseBinding"
                      >
                        保存
                      </el-button>
                    </div>
                  </div>
                </el-popover>

                <el-select
                  v-model="pickerModel"
                  :placeholder="modelSelectPlaceholder"
                  :disabled="!canStartSend"
                  size="small"
                  class="composer-pill model-select"
                  aria-label="选择对话模型"
                >
                  <template #prefix>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                      <path
                        d="M12 3 4.5 7.25v9.5L12 21l7.5-4.25v-9.5L12 3Z"
                        stroke-width="1.8"
                        stroke-linejoin="round"
                      />
                      <path
                        d="m4.8 7.5 7.2 4.1 7.2-4.1M12 11.6V21"
                        stroke-width="1.8"
                        stroke-linejoin="round"
                      />
                    </svg>
                  </template>
                  <el-option
                    v-if="defaultChatModelId"
                    :value="FOLLOW_DEFAULT_MODEL"
                    :label="`跟随默认（${defaultModelName ?? '默认模型'}）`"
                  />
                  <el-option
                    v-for="m in models"
                    :key="m.id"
                    :label="modelRoleLabel(m)"
                    :value="m.id"
                  />
                </el-select>
              </div>

              <button
                class="send-btn"
                :class="{
                  connecting: stream.phase.value === 'connecting',
                  stopping: stream.phase.value === 'streaming',
                }"
                :disabled="isSendButtonDisabled"
                :aria-label="sendLabel"
                :title="sendLabel"
                @click="stream.phase.value === 'streaming' ? handleStop() : handleSend()"
              >
                <span
                  v-if="stream.phase.value === 'connecting'"
                  class="send-loading"
                  aria-hidden="true"
                />
                <svg
                  v-else-if="stream.phase.value !== 'streaming'"
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
          <p class="composer-assistive">
            <span>Enter 发送</span>
            <span class="composer-secondary-separator" aria-hidden="true">·</span>
            <span class="composer-secondary-shortcut">Shift+Enter 换行</span>
            <span class="composer-disclaimer-separator" aria-hidden="true">·</span>
            <span class="composer-disclaimer">AI 回答可能不准确，请核对重要信息</span>
          </p>
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
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  min-height: 30px;
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
  width: 17px;
  height: 17px;
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
  width: 30px;
  height: 30px;
  min-height: 30px;
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
.session-generating {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 9px;
  font-weight: 900;
  color: var(--kf-hot);
  border: 1px solid currentColor;
  border-radius: 999px;
  padding: 1px 7px;
  animation: session-generating-pulse 1.2s ease-in-out infinite;
}
@keyframes session-generating-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
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
.binding-trigger.has-unavailable {
  color: #9a4a00;
  border-color: #d9974a;
}
.binding-trigger.has-unavailable::after {
  content: '⚠';
}
.binding-editor {
  display: grid;
  gap: 12px;
}
.binding-editor-head,
.binding-editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.binding-editor p {
  margin: 0;
  color: var(--kf-muted);
  font-size: 12px;
  line-height: 1.6;
}
.binding-options {
  height: fit-content;
  padding: 8px 10px;
  border: 1px solid var(--kf-line);
  border-radius: 10px;
}
.binding-options .el-checkbox-group {
  display: grid;
  gap: 6px;
}
.binding-options .el-checkbox {
  margin-right: 0;
}
.binding-unavailable {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.binding-unavailable span {
  border: 1px solid #d9974a;
  border-radius: 999px;
  padding: 3px 8px;
  color: #8a4300;
  background: #fff6e8;
  font-size: 11px;
}
.binding-stale-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.binding-chip-remove {
  border: none;
  background: transparent;
  color: #8a4300;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  padding: 0;
}
.binding-chip-remove:hover {
  color: #d9534f;
}
.binding-editor-actions {
  justify-content: flex-end;
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
  font-size: 0.8rem;
  font-weight: 700;
  width: fit-content;
}
.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--kf-muted);
  font-size: 0.8rem;
  font-weight: 700;
  margin-top: 2px;
}
.thinking-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.thinking-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--kf-hot);
  animation: thinking-bounce 1.2s ease-in-out infinite;
}
.thinking-dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.thinking-dots i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes thinking-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }
  30% {
    transform: translateY(-3px);
    opacity: 1;
  }
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
  background: var(--kf-white);
}
.composebox {
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border: 1px solid var(--kf-line);
  background: var(--kf-white);
  border-radius: 18px;
  min-height: 132px;
  overflow: hidden;
  box-shadow: 0 12px 36px rgba(45, 36, 23, 0.08);
  transition:
    box-shadow var(--kf-duration-fast) var(--kf-ease),
    border-color var(--kf-duration-fast) var(--kf-ease);
}
.composebox:focus-within {
  border-color: var(--kf-green);
  box-shadow:
    0 0 0 2px var(--kf-green-soft),
    0 12px 36px rgba(45, 36, 23, 0.08);
}
.composebox :deep(.el-textarea) {
  width: 100%;
  min-width: 0;
  padding: 18px 18px 8px;
  border-radius: inherit;
  overflow: hidden;
}
.composebox :deep(.el-textarea__inner) {
  box-sizing: border-box;
  font-family: inherit;
  font-size: 16px;
  font-weight: 500;
  line-height: 28px;
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
  line-height: 28px;
  opacity: 0.85;
}
.composebox :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}
.composer-toolbar {
  box-sizing: border-box;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 10px 10px;
}
.composer-tools {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}
.composer-pill.binding-trigger {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 220px;
  height: var(--kf-touch-min);
  padding-inline: 12px;
  border-color: var(--kf-line);
  border-radius: var(--kf-radius-pill);
  background: var(--kf-paper);
  color: var(--kf-ink);
  box-shadow: none;
  overflow: hidden;
}
.composer-pill.binding-trigger:hover:not(:disabled) {
  border-color: var(--kf-green);
  color: var(--kf-green);
  background: var(--kf-green-soft);
}
.composer-pill.binding-trigger svg,
.model-select :deep(.el-select__prefix svg) {
  flex: 0 0 auto;
  width: 17px;
  height: 17px;
}
.composer-pill-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-select {
  flex: 0 1 220px;
  width: 220px;
  min-width: 130px;
}
.model-select :deep(.el-select__wrapper) {
  min-height: var(--kf-touch-min);
  padding-inline: 12px;
  border: 1px solid var(--kf-line);
  border-radius: var(--kf-radius-pill);
  background: var(--kf-paper);
  box-shadow: none;
  transition:
    border-color var(--kf-duration-fast) var(--kf-ease),
    background var(--kf-duration-fast) var(--kf-ease);
}
.model-select :deep(.el-select__wrapper:hover),
.model-select :deep(.el-select__wrapper.is-focused) {
  border-color: var(--kf-green);
  background: var(--kf-green-soft);
  box-shadow: none;
}
.model-select :deep(.el-select__selected-item),
.model-select :deep(.el-select__placeholder) {
  min-width: 0;
  color: var(--kf-ink);
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.send-btn {
  flex: 0 0 var(--kf-touch-min);
  width: var(--kf-touch-min);
  height: var(--kf-touch-min);
  min-height: var(--kf-touch-min);
  border-radius: 50%;
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
.send-btn.connecting:disabled {
  background: var(--kf-paper-2);
  border-color: var(--kf-line);
  color: var(--kf-green);
}
.send-btn svg {
  width: 18px;
  height: 18px;
}
.send-loading {
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: composer-spin 700ms linear infinite;
}
@keyframes composer-spin {
  to {
    transform: rotate(360deg);
  }
}
.stop-square {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: currentColor;
}
.composer-assistive {
  min-height: 17px;
  margin: 7px 8px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: var(--kf-muted);
  font-size: 10px;
  font-weight: 700;
  line-height: 1.5;
  text-align: center;
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
    justify-content: flex-end;
    margin-top: 4px;
  }
}
@media (max-width: 620px) {
  .chat-input {
    padding: 8px 10px 10px;
  }
  .composebox {
    border-radius: 16px;
  }
  .composer-toolbar {
    gap: 8px;
    padding-inline: 8px;
  }
  .composer-tools {
    gap: 6px;
  }
  .composer-pill.binding-trigger {
    max-width: 150px;
    padding-inline: 10px;
  }
  .model-select {
    flex-basis: 170px;
    width: 170px;
  }
  .composer-disclaimer,
  .composer-disclaimer-separator {
    display: none;
  }
}
@media (max-width: 420px) {
  .composer-secondary-shortcut,
  .composer-secondary-separator {
    display: none;
  }
  .composer-assistive {
    justify-content: flex-start;
  }
}
@media (prefers-reduced-motion: reduce) {
  .send-loading {
    animation-duration: 1.4s;
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
