// 会话生成流管理器（chatGeneration store）测试：live 状态累积、终态流转、停止/清理。
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useChatGenerationStore } from '../stores/chatGeneration'

/** 构造一个可被 startStream 使用的 start：模拟 fetch 流，事件通过 dispatch 分发。 */
function streamDriver() {
  const events: Array<{ name: string; data: unknown }> = []
  let aborted = false
  let dispatchFn: ((name: string, data: unknown) => void) | null = null
  const controller = {
    get signal() {
      return { aborted }
    },
    abort() {
      aborted = true
    },
  }
  const start = (dispatch: (name: string, data: unknown) => void) => {
    dispatchFn = dispatch
    return controller as unknown as AbortController
  }
  const emit = (name: string, data: unknown) => {
    events.push({ name, data })
    dispatchFn?.(name, data)
  }
  return { start, emit, controller, events }
}

describe('chatGeneration store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('按会话累积 live 增量内容与消息 id', () => {
    const store = useChatGenerationStore()
    const driver = streamDriver()
    const onEvent = vi.fn()

    store.startStream('10', driver.start, onEvent)

    expect(store.isGenerating('10')).toBe(true)
    driver.emit('generation.started', { assistantMessageId: '100' })
    driver.emit('content.delta', { assistantMessageId: '100', delta: '你好' })
    driver.emit('content.delta', { assistantMessageId: '100', delta: '，世界' })

    const live = store.liveOf('10')!
    expect(live.messageId).toBe('100')
    expect(live.content).toBe('你好，世界')
    // 组件回调同步收到每个事件
    expect(onEvent).toHaveBeenCalledTimes(3)
  })

  it('completed/failed 事件流转 live 状态，且互不影响其他会话', () => {
    const store = useChatGenerationStore()
    const driverA = streamDriver()
    const driverB = streamDriver()

    store.startStream('10', driverA.start, vi.fn())
    store.startStream('20', driverB.start, vi.fn())
    driverA.emit('generation.completed', { assistantMessageId: '100' })

    expect(store.liveOf('10')?.status).toBe('completed')
    expect(store.liveOf('10')?.content).toBe('')
    expect(store.isGenerating('10')).toBe(false)
    // 会话 B 仍在生成（跨会话并发生成）
    expect(store.isGenerating('20')).toBe(true)

    driverB.emit('generation.failed', { errorCode: 'MODEL_CALL_FAILED' })
    expect(store.liveOf('20')?.status).toBe('failed')
    expect(store.liveOf('20')?.errorCode).toBe('MODEL_CALL_FAILED')
  })

  it('stopStream 中止本地流并清除条目，clearGeneration 仅清除状态', () => {
    const store = useChatGenerationStore()
    const driver = streamDriver()

    store.startStream('10', driver.start, vi.fn())
    expect(store.isGenerating('10')).toBe(true)

    store.stopStream('10')
    expect(driver.controller.signal.aborted).toBe(true)
    expect(store.liveOf('10')).toBeUndefined()
  })

  it('重复 startStream 覆盖旧条目（同会话后端 409 兜底，前端仅防御）', () => {
    const store = useChatGenerationStore()
    const driverA = streamDriver()
    const driverB = streamDriver()

    store.startStream('10', driverA.start, vi.fn())
    driverA.emit('content.delta', { assistantMessageId: '100', delta: '旧内容' })
    store.startStream('10', driverB.start, vi.fn())
    driverB.emit('content.delta', { assistantMessageId: '101', delta: '新内容' })

    const live = store.liveOf('10')!
    expect(live.messageId).toBe('101')
    expect(live.content).toBe('新内容')
  })
})
