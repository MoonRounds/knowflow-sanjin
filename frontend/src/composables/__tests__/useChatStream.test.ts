// useChatStream 测试：SSE 事件路由、增量累积、失败状态与 reconcile 对账。
import { describe, expect, it } from 'vitest'
import { dispatchSseEvent, type StreamEventHandlers, useChatStream } from '../useChatStream'
import type { ConversationResponse, MessageResponse } from '../../api/types/conversation'

describe('dispatchSseEvent', () => {
  it('routes generation.started with assistantMessageId', () => {
    const seen: string[] = []
    const handlers: StreamEventHandlers = {
      onStarted: (d) => seen.push(`started:${d.assistantMessageId}`),
    }
    dispatchSseEvent('generation.started', { assistantMessageId: '42' }, handlers)
    expect(seen).toEqual(['started:42'])
  })

  it('routes content.delta and accumulates deltas', () => {
    const deltas: string[] = []
    const handlers: StreamEventHandlers = {
      onDelta: (d) => deltas.push(d.delta ?? ''),
    }
    dispatchSseEvent('content.delta', { assistantMessageId: '42', delta: 'Hel' }, handlers)
    dispatchSseEvent('content.delta', { assistantMessageId: '42', delta: 'lo' }, handlers)
    expect(deltas).toEqual(['Hel', 'lo'])
  })

  it('routes generation.completed with content, active and usage', () => {
    let completed: { content?: string; active?: boolean } = {}
    const handlers: StreamEventHandlers = {
      onCompleted: (d) => (completed = d),
    }
    dispatchSseEvent(
      'generation.completed',
      { assistantMessageId: '42', content: 'Hello', active: true, usage: { totalTokens: 9 } },
      handlers,
    )
    expect(completed.content).toBe('Hello')
    expect(completed.active).toBe(true)
  })

  it('routes generation.failed with errorCode', () => {
    let failed: { errorCode?: string } = {}
    const handlers: StreamEventHandlers = {
      onFailed: (d) => (failed = d),
    }
    dispatchSseEvent('generation.failed', { errorCode: 'MODEL_CALL_TIMEOUT' }, handlers)
    expect(failed.errorCode).toBe('MODEL_CALL_TIMEOUT')
  })

  it('routes sources.available with ragStatus and sources', () => {
    let seen: { ragStatus?: string; sources?: unknown[] } = {}
    const handlers: StreamEventHandlers = {
      onSourcesAvailable: (d) => (seen = { ragStatus: d.ragStatus, sources: d.sources }),
    }
    dispatchSseEvent(
      'sources.available',
      {
        assistantMessageId: '42',
        ragStatus: 'USED',
        sources: [{ sourceId: 'c1', itemTitle: 'Note', cited: true }],
      },
      handlers,
    )
    expect(seen.ragStatus).toBe('USED')
    expect(seen.sources).toHaveLength(1)
  })

  it('ignores unknown events and generation.stage', () => {
    const handlers: StreamEventHandlers = {
      onStarted: () => {
        throw new Error('should not fire')
      },
    }
    expect(() => dispatchSseEvent('generation.stage', { stage: 'x' }, handlers)).not.toThrow()
    expect(() => dispatchSseEvent('unknown.event', {}, handlers)).not.toThrow()
  })
})

function makeConversation(id: string): ConversationResponse {
  return { id, title: 'c', activeGenerationMessageId: undefined }
}

function runStream(reconcile: () => void, events: Array<[string, unknown]>) {
  let messages: MessageResponse[] = []
  const stream = useChatStream({
    getConversation: () => makeConversation('1'),
    getMessages: () => messages,
    setMessages: (m) => (messages = m),
    reconcile,
  })
  for (const [name, data] of events) {
    dispatchSseEvent(name, data, stream.handlers)
  }
  return stream
}

describe('useChatStream reconcile', () => {
  it('calls reconcile after generation.completed', () => {
    let reconciled = 0
    runStream(
      () => reconciled++,
      [
        ['generation.started', { assistantMessageId: '42' }],
        ['generation.completed', { assistantMessageId: '42', content: 'hi', active: true }],
      ],
    )
    expect(reconciled).toBe(1)
  })

  it('calls reconcile after generation.failed', () => {
    let reconciled = 0
    runStream(
      () => reconciled++,
      [
        ['generation.started', { assistantMessageId: '42' }],
        ['generation.failed', { errorCode: 'MODEL_CALL_TIMEOUT' }],
      ],
    )
    expect(reconciled).toBe(1)
  })

  it('marks failed message status on generation.failed', () => {
    let messages: MessageResponse[] = []
    const stream = useChatStream({
      getConversation: () => makeConversation('1'),
      getMessages: () => messages,
      setMessages: (m) => (messages = m),
    })
    dispatchSseEvent('generation.started', { assistantMessageId: '42' }, stream.handlers)
    dispatchSseEvent(
      'generation.failed',
      { errorCode: 'MODEL_CALL_TIMEOUT', detail: 'timeout' },
      stream.handlers,
    )
    expect(messages[0].generationStatus).toBe('FAILED')
    expect(messages[0].errorCode).toBe('MODEL_CALL_TIMEOUT')
    expect(stream.streaming.value).toBe(false)
  })

  it('applies ragStatus and sources to the pending assistant on sources.available', () => {
    let messages: MessageResponse[] = []
    const stream = useChatStream({
      getConversation: () => makeConversation('1'),
      getMessages: () => messages,
      setMessages: (m) => (messages = m),
    })
    dispatchSseEvent('generation.started', { assistantMessageId: '42' }, stream.handlers)
    dispatchSseEvent(
      'sources.available',
      {
        assistantMessageId: '42',
        ragStatus: 'USED',
        sources: [{ sourceId: 'c1', itemTitle: 'Note', cited: true }],
      },
      stream.handlers,
    )
    expect(messages[0].ragStatus).toBe('USED')
    expect(messages[0].sources).toHaveLength(1)
    expect(messages[0].sources![0].cited).toBe(true)
  })
})
