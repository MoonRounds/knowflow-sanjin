import { computed, ref } from 'vue'
import type {
  ConversationResponse,
  MessageResponse,
  RetrievedSource,
} from '../api/types/conversation'

export interface StreamEventHandlers {
  onStarted?: (data: { assistantMessageId?: string }) => void
  onDelta?: (data: { assistantMessageId?: string; delta?: string }) => void
  onSourcesAvailable?: (data: {
    assistantMessageId?: string
    ragStatus?: string
    sources?: RetrievedSource[]
  }) => void
  onCompleted?: (data: {
    assistantMessageId?: string
    content?: string
    active?: boolean
    usage?: unknown
  }) => void
  onFailed?: (data: { errorCode?: string; detail?: string }) => void
  onCancelled?: () => void
}

/** SSE 事件类型到回调的映射。 */
export function dispatchSseEvent(eventName: string, data: unknown, handlers: StreamEventHandlers) {
  const d = data as Record<string, unknown>
  switch (eventName) {
    case 'generation.started':
      handlers.onStarted?.({ assistantMessageId: d.assistantMessageId as string | undefined })
      break
    case 'generation.stage':
      break
    case 'content.delta':
      handlers.onDelta?.({
        assistantMessageId: d.assistantMessageId as string | undefined,
        delta: d.delta as string | undefined,
      })
      break
    case 'sources.available':
      handlers.onSourcesAvailable?.({
        assistantMessageId: d.assistantMessageId as string | undefined,
        ragStatus: d.ragStatus as string | undefined,
        sources: d.sources as RetrievedSource[] | undefined,
      })
      break
    case 'generation.completed':
      handlers.onCompleted?.({
        assistantMessageId: d.assistantMessageId as string | undefined,
        content: d.content as string | undefined,
        active: d.active as boolean | undefined,
        usage: d.usage,
      })
      break
    case 'generation.failed':
      handlers.onFailed?.({
        errorCode: d.errorCode as string | undefined,
        detail: d.detail as string | undefined,
      })
      break
    default:
      break
  }
}

export interface UseChatStreamOptions {
  getConversation: () => ConversationResponse | null
  getMessages: () => MessageResponse[]
  setMessages: (messages: MessageResponse[]) => void
  /** 流结束后对账（重新拉取服务器最终状态），失败/取消/断连时确保 UI 与 DB 一致。 */
  reconcile?: () => void
}

/**
 * Chat 流式状态管理：维护正在生成的 assistant 消息（临时置灰），按 SSE 事件增量更新。
 */
export function useChatStream(options: UseChatStreamOptions) {
  const streaming = ref(false)
  const streamError = ref<string | null>(null)

  /** 本地占位的流式 assistant 消息（真实 assistantMessageId 未知前使用）。 */
  const pendingAssistant = ref<MessageResponse | null>(null)

  const currentConversation = computed(() => options.getConversation())

  function appendAssistantMessage(message: MessageResponse) {
    const messages = [...options.getMessages()]
    // 若存在占位消息则替换
    const idx = messages.findIndex((m) => m.id === (pendingAssistant.value?.id ?? '__pending__'))
    if (pendingAssistant.value && idx >= 0) {
      messages[idx] = message
    } else {
      messages.push(message)
    }
    options.setMessages(messages)
  }

  function handleStarted(data: { assistantMessageId?: string }) {
    streaming.value = true
    streamError.value = null
    if (data.assistantMessageId) {
      pendingAssistant.value = {
        id: data.assistantMessageId,
        conversationId: currentConversation.value?.id,
        role: 'ASSISTANT',
        content: '',
        generationStatus: 'GENERATING',
        active: false,
      }
      appendAssistantMessage(pendingAssistant.value!)
    }
  }

  function handleDelta(data: { delta?: string }) {
    if (!data.delta) return
    const messages = [...options.getMessages()]
    const target = messages.find((m) => m.id === (pendingAssistant.value?.id ?? ''))
    if (target) {
      target.content = (target.content ?? '') + data.delta
      options.setMessages(messages)
    }
  }

  function handleSourcesAvailable(data: {
    assistantMessageId?: string
    ragStatus?: string
    sources?: RetrievedSource[]
  }) {
    const messages = [...options.getMessages()]
    const id = data.assistantMessageId ?? pendingAssistant.value?.id
    const target = messages.find((m) => m.id === id)
    if (target) {
      if (data.ragStatus) target.ragStatus = data.ragStatus
      if (data.sources) target.sources = data.sources
    }
    options.setMessages(messages)
  }

  function handleCompleted(data: {
    assistantMessageId?: string
    content?: string
    active?: boolean
    usage?: unknown
  }) {
    const messages = [...options.getMessages()]
    const id = data.assistantMessageId ?? pendingAssistant.value?.id
    const target = messages.find((m) => m.id === id)
    if (target) {
      target.content = data.content ?? target.content
      target.generationStatus = 'COMPLETED'
      target.active = data.active ?? false
      target.usage = data.usage as MessageResponse['usage']
    }
    options.setMessages(messages)
    pendingAssistant.value = null
    streaming.value = false
    options.reconcile?.()
  }

  function handleFailed(data: { errorCode?: string; detail?: string }) {
    const messages = [...options.getMessages()]
    const target = messages.find((m) => m.id === (pendingAssistant.value?.id ?? ''))
    if (target) {
      target.generationStatus = 'FAILED'
      target.errorCode = data.errorCode
    }
    options.setMessages(messages)
    pendingAssistant.value = null
    streaming.value = false
    streamError.value =
      data.errorCode === '生成已取消' ? '已取消' : (data.detail ?? data.errorCode ?? '生成失败')
    options.reconcile?.()
  }

  const handlers: StreamEventHandlers = {
    onStarted: handleStarted,
    onDelta: handleDelta,
    onSourcesAvailable: handleSourcesAvailable,
    onCompleted: handleCompleted,
    onFailed: handleFailed,
  }

  return {
    streaming,
    streamError,
    handlers,
    dispatchSseEvent,
  }
}
