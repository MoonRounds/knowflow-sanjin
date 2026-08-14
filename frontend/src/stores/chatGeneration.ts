import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dispatchSseEvent } from '../composables/useChatStream'

export type LiveGenerationStatus = 'generating' | 'completed' | 'failed' | 'cancelled'

export interface LiveGeneration {
  status: LiveGenerationStatus
  /** 后端 assistant 消息 id（generation.started / content.delta 携带）。 */
  messageId?: string
  /** 已累积的增量内容，供切回会话时即时展示。 */
  content: string
  errorCode?: string
}

/**
 * 会话生成流管理器（模块级，跨路由存活）。
 *
 * 持有每个会话进行中/刚结束的生成状态与本地流（AbortController）。切换模块（组件卸载）或切换会话
 * 都不会中断流：SSE 事件继续被消费并累积到 generations，回答在后台完成后可由服务端落库、
 * 重新拉取历史获得（配合后端断连静默模式）。组件卸载后不 abort，因此闭包持有的回调仍会收到事件，
 * 但组件侧 dispatch 有 generation token 守卫，旧流不会污染新会话的 UI。
 */
export const useChatGenerationStore = defineStore('chatGeneration', () => {
  const generations = ref<Record<string, LiveGeneration>>({})
  /** 非响应式的本地流控制器（仅用于停止时 abort），按会话 id 索引。 */
  const controllers = new Map<string, AbortController>()

  function liveOf(conversationId: string): LiveGeneration | undefined {
    return generations.value[conversationId]
  }

  function isGenerating(conversationId: string): boolean {
    return generations.value[conversationId]?.status === 'generating'
  }

  /**
   * 发起一轮生成。start 负责发起 fetch 流并返回 AbortController；SSE 事件先喂给 store
   * 累积 live 状态（消息 id / 增量内容 / 终态），再交给 onEvent 更新组件消息列表。
   */
  function startStream(
    conversationId: string,
    start: (dispatch: (eventName: string, data: unknown) => void) => AbortController,
    onEvent: (eventName: string, data: unknown) => void,
  ) {
    generations.value[conversationId] = { status: 'generating', content: '' }
    const controller = start((eventName, data) => {
      dispatchSseEvent(eventName, data, {
        onStarted: (d) => {
          const live = generations.value[conversationId]
          if (live && d.assistantMessageId) live.messageId = d.assistantMessageId
        },
        onDelta: (d) => {
          const live = generations.value[conversationId]
          if (!live) return
          if (d.assistantMessageId) live.messageId = d.assistantMessageId
          if (d.delta) live.content += d.delta
        },
        onCompleted: () => {
          const live = generations.value[conversationId]
          if (live) live.status = 'completed'
        },
        onFailed: (d) => {
          const live = generations.value[conversationId]
          if (live) {
            live.status = 'failed'
            live.errorCode = d.errorCode
          }
        },
      })
      onEvent(eventName, data)
    })
    controllers.set(conversationId, controller)
  }

  /** 停止本会话的本地流并清除状态（配合 POST /stop 使用；后端取消标志优先，不会误分类为断连失败）。 */
  function stopStream(conversationId: string) {
    controllers.get(conversationId)?.abort()
    controllers.delete(conversationId)
    delete generations.value[conversationId]
  }

  /** 清除某会话的生成状态（对账完成后调用，防止已完成条目残留）。 */
  function clearGeneration(conversationId: string) {
    controllers.delete(conversationId)
    delete generations.value[conversationId]
  }

  return { generations, liveOf, isGenerating, startStream, stopStream, clearGeneration }
})
