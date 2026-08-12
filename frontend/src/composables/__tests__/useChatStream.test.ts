// useChatStream 测试：SSE 事件路由、phase 状态机、generation token 守卫与 reconcile 对账。
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
    dispatchSseEvent('generation.failed', { errorCode: '模型调用超时' }, handlers)
    expect(failed.errorCode).toBe('模型调用超时')
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

  it('passes router diagnostic through sources.available', () => {
    let seen: unknown = null
    const handlers: StreamEventHandlers = {
      onSourcesAvailable: (d) => (seen = d.router),
    }
    dispatchSseEvent(
      'sources.available',
      {
        assistantMessageId: '42',
        ragStatus: 'USED',
        router: {
          needRag: true,
          knowledgeBaseIds: ['kb1'],
          retrievalQuery: 'ConcurrentHashMap 线程安全',
          routeScores: [{ knowledgeBaseId: 'kb1', score: 0.92 }],
        },
      },
      handlers,
    )
    expect(seen).toEqual({
      needRag: true,
      knowledgeBaseIds: ['kb1'],
      retrievalQuery: 'ConcurrentHashMap 线程安全',
      routeScores: [{ knowledgeBaseId: 'kb1', score: 0.92 }],
    })
  })

  it('keeps router undefined when sources.available has no router', () => {
    let seen: unknown = 'sentinel'
    const handlers: StreamEventHandlers = {
      onSourcesAvailable: (d) => (seen = d.router),
    }
    dispatchSseEvent(
      'sources.available',
      { assistantMessageId: '42', ragStatus: 'NOT_NEEDED' },
      handlers,
    )
    expect(seen).toBeUndefined()
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

function makeStream(reconcile?: () => void) {
  let messages: MessageResponse[] = []
  const stream = useChatStream({
    getConversation: () => makeConversation('1'),
    getMessages: () => messages,
    setMessages: (m) => (messages = m),
    reconcile,
  })
  return { stream, getMessages: () => messages }
}

describe('useChatStream phase 状态机', () => {
  it('starts at idle', () => {
    const { stream } = makeStream()
    expect(stream.phase.value).toBe('idle')
  })

  it('moves idle → connecting → streaming on started → completed', () => {
    const { stream } = makeStream()
    const dispatch = stream.startGeneration()
    expect(stream.phase.value).toBe('connecting')
    dispatch('generation.started', { assistantMessageId: '42' })
    expect(stream.phase.value).toBe('streaming')
    dispatch('generation.completed', {
      assistantMessageId: '42',
      content: 'hi',
      active: true,
    })
    expect(stream.phase.value).toBe('completed')
  })

  it('moves to failed on generation.failed', () => {
    const { stream } = makeStream()
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    dispatch('generation.failed', { errorCode: '模型调用超时' })
    expect(stream.phase.value).toBe('failed')
  })

  it('stopGeneration resets to idle and clears pending state', () => {
    const { stream, getMessages } = makeStream()
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    dispatch('content.delta', { assistantMessageId: '42', delta: 'Hi' })
    expect(getMessages()[0].content).toBe('Hi')
    stream.stopGeneration()
    expect(stream.phase.value).toBe('idle')
    expect(getMessages().some((m) => m.generationStatus === 'GENERATING')).toBe(false)
  })
})

describe('useChatStream generation token 守卫', () => {
  it('drops events from a stale generation after stopGeneration', () => {
    const { stream, getMessages } = makeStream()
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    stream.stopGeneration()
    // 过期流继续用旧 dispatch 推送，必须被忽略
    dispatch('content.delta', { assistantMessageId: '42', delta: 'pollute' })
    dispatch('generation.completed', {
      assistantMessageId: '42',
      content: 'pollute',
      active: true,
    })
    expect(stream.phase.value).toBe('idle')
    expect(getMessages().some((m) => m.content === 'pollute')).toBe(false)
  })

  it('new generation invalidates the previous dispatch', () => {
    const { stream, getMessages } = makeStream()
    const oldDispatch = stream.startGeneration()
    oldDispatch('generation.started', { assistantMessageId: '42' })
    // 新一轮开始：旧 dispatch 的后续事件被丢弃
    const newDispatch = stream.startGeneration()
    oldDispatch('content.delta', { assistantMessageId: '42', delta: 'old' })
    newDispatch('generation.started', { assistantMessageId: '43' })
    expect(stream.phase.value).toBe('streaming')
    expect(getMessages().some((m) => m.content === 'old')).toBe(false)
    // 新流的 delta 正常累积
    newDispatch('content.delta', { assistantMessageId: '43', delta: 'new' })
    expect(getMessages().some((m) => m.content === 'new')).toBe(true)
  })
})

describe('useChatStream reconcile', () => {
  it('reconciles after generation.completed is durably committed', () => {
    let reconciled = 0
    const { stream } = makeStream(() => reconciled++)
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    dispatch('generation.completed', {
      assistantMessageId: '42',
      content: 'hi',
      active: true,
    })
    expect(reconciled).toBe(1)
  })

  it('calls reconcile after generation.failed', () => {
    let reconciled = 0
    const { stream } = makeStream(() => reconciled++)
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    dispatch('generation.failed', { errorCode: '模型调用超时' })
    expect(reconciled).toBe(1)
  })

  it('marks failed message status on generation.failed', () => {
    const { stream, getMessages } = makeStream()
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    dispatch('generation.failed', { errorCode: '模型调用超时', detail: 'timeout' })
    expect(getMessages()[0].generationStatus).toBe('FAILED')
    expect(getMessages()[0].errorCode).toBe('模型调用超时')
    expect(stream.phase.value).toBe('failed')
  })

  it('applies ragStatus and sources to the pending assistant on sources.available', () => {
    const { stream, getMessages } = makeStream()
    const dispatch = stream.startGeneration()
    dispatch('generation.started', { assistantMessageId: '42' })
    dispatch('sources.available', {
      assistantMessageId: '42',
      ragStatus: 'USED',
      sources: [{ sourceId: 'c1', itemTitle: 'Note', cited: true }],
    })
    expect(getMessages()[0].ragStatus).toBe('USED')
    expect(getMessages()[0].sources).toHaveLength(1)
    expect(getMessages()[0].sources![0].cited).toBe(true)
  })
})
