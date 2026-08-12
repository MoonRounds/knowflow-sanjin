// Chat 三栏工作台的纯函数：路由轨迹推导、会话按日期分组、按关键词过滤。
import type { ConversationResponse } from '../api/types/conversation'
import type { RouterDiagnostic } from '../composables/useChatStream'
import { inferNeedRag } from './rag'

export interface RouterStep {
  label: string
  state: 'done' | 'skipped'
  detail: string
}

/** 由 SSE Router 诊断 + ragStatus 推导 4 步路由轨迹（对应设计稿 Chat Inspector）。 */
export function deriveRouterSteps(
  router: RouterDiagnostic | undefined,
  ragStatus?: string | null,
): RouterStep[] {
  const hasRouter = router !== undefined
  const needRag = inferNeedRag(router, ragStatus)
  const kbCount = router?.knowledgeBaseIds?.length ?? 0

  const retrievalState: 'done' | 'skipped' =
    !needRag || ragStatus === 'NOT_NEEDED' ? 'skipped' : 'done'
  const retrievalDetail = (() => {
    if (retrievalState === 'skipped') return '本轮跳过，避免无意义召回'
    if (ragStatus === 'NO_RELEVANT_CONTEXT') return '无足够相关内容，未注入生成'
    if (ragStatus === 'DEGRADED') return '检索降级，退回普通回答'
    if (kbCount > 0)
      return `命中 ${kbCount} 个知识库${router?.retrievalQuery ? ` · ${router.retrievalQuery}` : ''}`
    return '已检索并注入上下文'
  })()
  // 历史消息不回放 router 诊断，只保留 ragStatus；由展示层标注"不保留细节"
  const historyNote = hasRouter ? '' : ' · 历史消息不保留路由细节'

  return [
    { label: '理解问题', state: 'done', detail: '结合当前会话上下文理解提问' },
    {
      label: '判断是否需要 RAG',
      state: 'done',
      detail: needRag ? '判定需要调用个人知识' : '当前追问依赖通用知识即可',
    },
    { label: '个人知识检索', state: retrievalState, detail: retrievalDetail + historyNote },
    {
      label: '模型生成',
      state: 'done',
      detail: needRag ? '注入检索上下文后生成' : '以会话上下文生成',
    },
  ]
}

export interface ConversationGroup {
  label: '今天' | '更早'
  items: ConversationResponse[]
}

/** 按 createdAt 把会话分为"今天 / 更早"两组（无 createdAt 归入更早）。 */
export function groupConversationsByDate(
  conversations: ConversationResponse[],
): ConversationGroup[] {
  if (conversations.length === 0) return []
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const today: ConversationResponse[] = []
  const earlier: ConversationResponse[] = []
  for (const c of conversations) {
    const t = c.createdAt ? new Date(c.createdAt).getTime() : 0
    if (t >= startOfToday) today.push(c)
    else earlier.push(c)
  }
  const groups: ConversationGroup[] = []
  if (today.length) groups.push({ label: '今天', items: today })
  if (earlier.length) groups.push({ label: '更早', items: earlier })
  return groups
}

/** 按标题子串过滤（忽略大小写与首尾空白；空关键词返回全部）。 */
export function filterConversations(
  conversations: ConversationResponse[],
  keyword: string,
): ConversationResponse[] {
  const kw = keyword.trim().toLowerCase()
  if (!kw) return conversations
  return conversations.filter((c) => c.title?.toLowerCase().includes(kw))
}
