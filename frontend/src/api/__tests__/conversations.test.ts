// conversations API 客户端测试：CRUD、游标查询、SSE 流解析与错误映射。
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createConversation,
  deleteConversation,
  getConversation,
  listConversations,
  listMessages,
  streamSend,
  updateConversation,
} from '../conversations'

function stubFetchJson(status: number, body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: async () => body,
    }),
  )
}

function stubFetchSse(events: Array<{ event: string; data: string }>) {
  const lines = events.flatMap((e) => ['event: ' + e.event, 'data: ' + e.data, '', '']).join('\n')
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(lines))
      controller.close()
    },
  })
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: stream,
    }),
  )
}

describe('conversations api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists conversations', async () => {
    stubFetchJson(200, [{ id: '1', title: 'a' }])
    const list = await listConversations()
    expect(list).toHaveLength(1)
  })

  it('creates a conversation', async () => {
    stubFetchJson(201, { id: '2', title: 'new' })
    const created = await createConversation({ title: 'new' })
    expect(created.id).toBe('2')
  })

  it('gets and updates a conversation', async () => {
    stubFetchJson(200, { id: '3', title: 'x' })
    await getConversation('3')
    stubFetchJson(200, { id: '3', title: 'y' })
    const updated = await updateConversation('3', { title: 'y' })
    expect(updated.title).toBe('y')
  })

  it('deletes a conversation', async () => {
    stubFetchJson(204, undefined)
    await expect(deleteConversation('3')).resolves.toBeUndefined()
  })

  it('lists messages with cursor', async () => {
    stubFetchJson(200, { messages: [], nextBefore: '10' })
    const page = await listMessages('1', { before: '20', limit: 10 })
    expect(page.nextBefore).toBe('10')
  })

  it('throws ApiError on non-ok response', async () => {
    stubFetchJson(409, { detail: 'conflict', errorCode: '存在进行中的生成' })
    await expect(createConversation({ title: 'x' })).rejects.toMatchObject({
      status: 409,
      errorCode: '存在进行中的生成',
    })
  })

  it('streams SSE events and dispatches to onEvent', async () => {
    stubFetchSse([
      { event: 'generation.started', data: JSON.stringify({ assistantMessageId: '42' }) },
      { event: 'content.delta', data: JSON.stringify({ delta: 'Hel' }) },
      { event: 'generation.completed', data: JSON.stringify({ content: 'Hello', active: true }) },
    ])
    const received: Array<{ event: string; data: unknown }> = []
    streamSend('1', { clientMessageId: 'c1', content: 'hi' }, (event, data) => {
      received.push({ event, data })
    })
    await vi.waitFor(() => expect(received.length).toBeGreaterThanOrEqual(3))
    expect(received[0].event).toBe('generation.started')
    expect(received[1].event).toBe('content.delta')
    expect((received[2].data as { content?: string }).content).toBe('Hello')
  })
})
