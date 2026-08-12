// chat-workspace 纯函数测试：路由轨迹推导、会话按日期分组、按关键词过滤。
import { describe, expect, it } from 'vitest'
import type { ConversationResponse } from '../../api/types/conversation'
import type { RouterDiagnostic } from '../../composables/useChatStream'
import { deriveRouterSteps, filterConversations, groupConversationsByDate } from '../chat-workspace'

describe('deriveRouterSteps', () => {
  it('builds 4 ordered steps when router was called and used RAG', () => {
    const router: RouterDiagnostic = {
      needRag: true,
      knowledgeBaseIds: ['kb1', 'kb2'],
      retrievalQuery: 'ConcurrentHashMap 线程安全',
      routeScores: [{ knowledgeBaseId: 'kb1', score: 0.92 }],
    }
    const steps = deriveRouterSteps(router, 'USED')
    expect(steps).toHaveLength(4)
    expect(steps.map((s) => s.label)).toEqual([
      '理解问题',
      '判断是否需要 RAG',
      '个人知识检索',
      '模型生成',
    ])
    // 前两步 done，检索与生成各带说明
    expect(steps[0].state).toBe('done')
    expect(steps[1].state).toBe('done')
    expect(steps[2].detail).toContain('2 个知识库')
    expect(steps[3].detail).toContain('检索')
  })

  it('marks retrieval step skipped when router says no RAG', () => {
    const router: RouterDiagnostic = { needRag: false }
    const steps = deriveRouterSteps(router, 'NOT_NEEDED')
    expect(steps[2].state).toBe('skipped')
    expect(steps[2].detail).toContain('跳过')
  })

  it('falls back to ragStatus when no router diagnostic exists', () => {
    const steps = deriveRouterSteps(undefined, 'NOT_NEEDED')
    expect(steps[1].state).toBe('done')
    expect(steps[2].state).toBe('skipped')
  })

  it('marks history messages as lacking router detail', () => {
    const steps = deriveRouterSteps(undefined, 'USED')
    // 无 router 诊断 = 历史消息，检索步骤应诚实标注不保留细节
    expect(steps[2].detail).toContain('历史消息不保留路由细节')
    // 有 router 时不标注
    const liveSteps = deriveRouterSteps({ needRag: true, knowledgeBaseIds: ['kb1'] }, 'USED')
    expect(liveSteps[2].detail).not.toContain('历史消息不保留路由细节')
  })

  it('reflects no-relevant-context in retrieval step', () => {
    const router: RouterDiagnostic = { needRag: true, knowledgeBaseIds: ['kb1'] }
    const steps = deriveRouterSteps(router, 'NO_RELEVANT_CONTEXT')
    expect(steps[2].state).toBe('done')
    expect(steps[2].detail).toContain('无足够相关')
  })
})

describe('groupConversationsByDate', () => {
  it('separates today from earlier by createdAt', () => {
    const now = Date.now()
    const convs: ConversationResponse[] = [
      { id: '1', title: '今天', createdAt: new Date(now - 1000).toISOString() },
      { id: '2', title: '昨天', createdAt: new Date(now - 86400000 * 2).toISOString() },
    ]
    const groups = groupConversationsByDate(convs)
    expect(groups.map((g) => g.label)).toEqual(['今天', '更早'])
    expect(groups[0].items).toHaveLength(1)
    expect(groups[1].items).toHaveLength(1)
  })

  it('returns empty list for empty input', () => {
    expect(groupConversationsByDate([])).toEqual([])
  })
})

describe('filterConversations', () => {
  const convs: ConversationResponse[] = [
    { id: '1', title: 'ConcurrentHashMap 线程安全' },
    { id: '2', title: 'Redis 分布式锁' },
  ]
  it('matches by title substring, case-insensitive', () => {
    expect(filterConversations(convs, 'redis')).toEqual([convs[1]])
    expect(filterConversations(convs, '  ')).toEqual(convs)
  })
})
