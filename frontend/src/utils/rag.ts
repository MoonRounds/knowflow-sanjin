// RAG/Router 语义的集中解释：ragStatus 文案、needRag 推导、路由摘要。
import type { RouterDiagnostic } from '../composables/useChatStream'

/** ragStatus 枚举 → 中文文案（历史消息也携带 ragStatus，可直接映射）。 */
export const RAG_STATUS_TEXT: Record<string, string> = {
  NOT_AVAILABLE: '知识库不可用',
  NOT_NEEDED: '无需检索知识库',
  USED: '已使用个人知识',
  NO_RELEVANT_CONTEXT: '未找到相关内容',
  DEGRADED: '检索降级',
}

export function ragStatusText(ragStatus?: string | null): string | null {
  if (!ragStatus) return null
  return RAG_STATUS_TEXT[ragStatus] ?? ragStatus
}

/** 无 router 诊断时按 ragStatus 近似 needRag（实时流有 router；历史消息只有 ragStatus）。 */
export function inferNeedRag(
  router: RouterDiagnostic | undefined,
  ragStatus?: string | null,
): boolean {
  if (router !== undefined) return router.needRag
  return ragStatus !== 'NOT_NEEDED'
}

/** 路由摘要（route-badge）：优先 router 诊断，否则用 ragStatus 近似。 */
export function routerBadgeText(
  router: RouterDiagnostic | undefined,
  ragStatus?: string | null,
): string {
  if (router) {
    return router.needRag
      ? `已路由到 ${router.knowledgeBaseIds?.length ?? 0} 个知识库`
      : '本轮无需个人知识'
  }
  return ragStatusText(ragStatus) ?? ''
}
