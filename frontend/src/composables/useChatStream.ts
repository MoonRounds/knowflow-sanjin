import { computed, ref } from 'vue'
import type {
  ConversationResponse,
  MessageResponse,
  RetrievedSource,
} from '../api/types/conversation'

/** Router 诊断（对应 SSE sources.available 的 router 字段，仅发生过路由调用时出现）。 */
export interface RouterDiagnostic {
  needRag: boolean
  knowledgeBaseIds?: string[]
  retrievalQuery?: string
  routeScores?: Array<{ knowledgeBaseId: string; score: number }>
}

export interface StreamEventHandlers {
  onStarted?: (data: { assistantMessageId?: string }) => void
  onDelta?: (data: { assistantMessageId?: string; delta?: string }) => void
  onSourcesAvailable?: (data: {
    assistantMessageId?: string
    ragStatus?: string
    sources?: RetrievedSource[]
    router?: RouterDiagnostic
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
        router: d.router as RouterDiagnostic | undefined,
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

export type ChatPhase = 'idle' | 'connecting' | 'streaming' | 'completed' | 'failed'

/** 后端 errorCode 契约里的"取消"值（中文常量，见 ErrorCode.GENERATION_CANCELLED）。 */
export const CANCELLATION_ERROR_CODES: ReadonlySet<string> = new Set(['生成已取消'])

/** 判断某 errorCode 是否为"用户主动取消"而非真实失败。 */
export function isCancellationErrorCode(errorCode?: string): boolean {
  return !!errorCode && CANCELLATION_ERROR_CODES.has(errorCode)
}

export interface UseChatStreamOptions {
  getConversation: () => ConversationResponse | null
  getMessages: () => MessageResponse[]
  setMessages: (messages: MessageResponse[]) => void
  /** 流结束后对账（重新拉取服务器最终状态），失败/取消/断连时确保 UI 与 DB 一致。 */
  reconcile?: () => void
}

/**
 * Chat 流式状态机：维护 phase（idle→connecting→streaming→completed/failed）与
 * 正在生成的 assistant 占位消息。
 *
 * `startGeneration()` 返回一个捕获当前 generation token 的 dispatch 闭包。
 * 每轮生成/停止都会递增 token，旧 dispatch 闭包（旧流仍持有）因 token 不匹配而丢弃全部事件，
 * 从机制上防止页面切换/重复发送后旧流污染当前会话。
 */
export function useChatStream(options: UseChatStreamOptions) {
  const phase = ref<ChatPhase>('idle')
  const streamError = ref<string | null>(null)

  /** 本地占位的流式 assistant 消息（真实 assistantMessageId 未知前使用）。 */
  const pendingAssistant = ref<MessageResponse | null>(null)

  /** generation token：dispatch 闭包携带创建时的 token；token 已递增（新轮/已停止）则事件被丢弃。 */
  let generationToken = 0

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

  /** 事件只对"创建时的 token == 当前 token"生效（旧流/已停止流的闭包因 token 过期而失效）。 */
  function isGenerationActive(token: number): boolean {
    return token === generationToken
  }

  /** 开始一轮生成：置 connecting、递增 token；返回捕获该 token 的 dispatch 闭包。 */
  function startGeneration() {
    generationToken += 1
    phase.value = 'connecting'
    streamError.value = null
    pendingAssistant.value = null
    const token = generationToken
    return (eventName: string, data: unknown) =>
      dispatchSseEvent(eventName, data, makeHandlers(token))
  }

  /** 停止/放弃当前生成：置 idle、递增 token 使旧 dispatch 全部失效，并从消息列表移除占位。 */
  function stopGeneration() {
    generationToken += 1
    phase.value = 'idle'
    streamError.value = null
    if (pendingAssistant.value) {
      const id = pendingAssistant.value.id
      options.setMessages(options.getMessages().filter((m) => m.id !== id))
      pendingAssistant.value = null
    }
  }

  function handleStarted(data: { assistantMessageId?: string }, token: number) {
    if (!isGenerationActive(token)) return
    phase.value = 'streaming'
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

  function handleDelta(data: { delta?: string }, token: number) {
    if (!isGenerationActive(token)) return
    if (!data.delta) return
    const messages = [...options.getMessages()]
    const target = messages.find((m) => m.id === (pendingAssistant.value?.id ?? ''))
    if (target) {
      target.content = (target.content ?? '') + data.delta
      options.setMessages(messages)
    }
  }

  function handleSourcesAvailable(
    data: { assistantMessageId?: string; ragStatus?: string; sources?: RetrievedSource[] },
    token: number,
  ) {
    if (!isGenerationActive(token)) return
    const messages = [...options.getMessages()]
    const id = data.assistantMessageId ?? pendingAssistant.value?.id
    const target = messages.find((m) => m.id === id)
    if (target) {
      if (data.ragStatus) target.ragStatus = data.ragStatus
      if (data.sources) target.sources = data.sources
    }
    options.setMessages(messages)
  }

  function handleCompleted(
    data: { assistantMessageId?: string; content?: string; active?: boolean; usage?: unknown },
    token: number,
  ) {
    if (!isGenerationActive(token)) return
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
    phase.value = 'completed'
    // generation.completed 只会在后端 Tx2 提交并释放 active slot 后发送，可安全对账事实源。
    options.reconcile?.()
  }

  function handleFailed(data: { errorCode?: string; detail?: string }, token: number) {
    if (!isGenerationActive(token)) return
    const messages = [...options.getMessages()]
    const target = messages.find((m) => m.id === (pendingAssistant.value?.id ?? ''))
    if (target) {
      target.generationStatus = 'FAILED'
      target.errorCode = data.errorCode
    }
    options.setMessages(messages)
    pendingAssistant.value = null
    phase.value = 'failed'
    streamError.value = isCancellationErrorCode(data.errorCode)
      ? '已取消'
      : (data.errorCode ?? data.detail ?? '生成失败')
    options.reconcile?.()
  }

  /** 用"创建时刻"的 token 构造 handlers；旧 dispatch 持有的旧 token 与后续 generation 不匹配。 */
  function makeHandlers(token: number): StreamEventHandlers {
    return {
      onStarted: (d) => handleStarted(d, token),
      onDelta: (d) => handleDelta(d, token),
      onSourcesAvailable: (d) => handleSourcesAvailable(d, token),
      onCompleted: (d) => handleCompleted(d, token),
      onFailed: (d) => handleFailed(d, token),
    }
  }

  return {
    phase,
    streamError,
    startGeneration,
    stopGeneration,
  }
}
