import type {
  ConversationResponse,
  CreateConversationRequest,
  MessagePageResponse,
  MessageResponse,
  RegenerateRequest,
  SendMessageRequest,
  UpdateConversationRequest,
} from './types/conversation'
import { API_BASE, parseProblem, request } from './request'

export function listConversations(): Promise<ConversationResponse[]> {
  return request('/conversations')
}

export function getConversation(id: string): Promise<ConversationResponse> {
  return request(`/conversations/${id}`)
}

export function createConversation(
  payload: CreateConversationRequest,
): Promise<ConversationResponse> {
  return request('/conversations', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateConversation(
  id: string,
  payload: UpdateConversationRequest,
): Promise<ConversationResponse> {
  return request(`/conversations/${id}`, { method: 'PATCH', body: JSON.stringify(payload) })
}

export function deleteConversation(id: string): Promise<void> {
  return request(`/conversations/${id}`, { method: 'DELETE' })
}

export function listMessages(
  id: string,
  opts?: { before?: string; limit?: number },
): Promise<MessagePageResponse> {
  const params = new URLSearchParams()
  if (opts?.before) params.set('before', opts.before)
  if (opts?.limit) params.set('limit', String(opts.limit))
  const qs = params.toString()
  return request(`/conversations/${id}/messages${qs ? `?${qs}` : ''}`)
}

export function stopGeneration(id: string): Promise<void> {
  return request(`/conversations/${id}/stop`, { method: 'POST' })
}

/** 发送消息并读取 SSE 流。onEvent 按事件名回调；返回 AbortController 供取消。 */
export function streamSend(
  id: string,
  payload: SendMessageRequest,
  onEvent: (eventName: string, data: unknown) => void,
  onError?: (error: unknown) => void,
): AbortController {
  const controller = new AbortController()
  void (async () => {
    try {
      const response = await fetch(`${API_BASE}/conversations/${id}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify(payload),
        signal: controller.signal,
      })
      if (!response.ok) {
        throw await parseProblem(response)
      }
      if (!response.body) {
        throw new Error('response body is null')
      }
      await consumeEventStream(response.body, onEvent)
    } catch (error) {
      if (controller.signal.aborted) {
        return // 主动取消不视为错误
      }
      onError?.(error)
    }
  })()
  return controller
}

/** 重新生成并读取 SSE 流。 */
export function streamRegenerate(
  id: string,
  payload: RegenerateRequest | undefined,
  onEvent: (eventName: string, data: unknown) => void,
  onError?: (error: unknown) => void,
): AbortController {
  const controller = new AbortController()
  void (async () => {
    try {
      const response = await fetch(`${API_BASE}/conversations/${id}/messages/regenerate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: payload ? JSON.stringify(payload) : undefined,
        signal: controller.signal,
      })
      if (!response.ok) {
        throw await parseProblem(response)
      }
      if (!response.body) {
        throw new Error('response body is null')
      }
      await consumeEventStream(response.body, onEvent)
    } catch (error) {
      if (controller.signal.aborted) {
        return
      }
      onError?.(error)
    }
  })()
  return controller
}

async function consumeEventStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (eventName: string, data: unknown) => void,
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 按空行切分事件块；最后一段可能不完整，留到下一轮
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const parsed = parseEventBlock(block)
      if (parsed) {
        onEvent(parsed.name, parsed.data)
      }
    }
  }
  // 流结束时处理残留（如无空行结尾）
  if (buffer.trim().length > 0) {
    const parsed = parseEventBlock(buffer)
    if (parsed) {
      onEvent(parsed.name, parsed.data)
    }
  }
}

function parseEventBlock(block: string): { name: string; data: unknown } | null {
  let eventName: string | null = null
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (!eventName || dataLines.length === 0) return null
  const raw = dataLines.join('\n')
  let data: unknown = raw
  try {
    data = JSON.parse(raw)
  } catch {
    // 保留原始文本
  }
  return { name: eventName, data }
}

export type { MessageResponse }
