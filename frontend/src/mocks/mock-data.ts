/**
 * /flow 学习流首页的展示类型定义。
 *
 * 数据源已从 mock 切换为真实 API（见 useFlowData）。本文件仅保留组件所需的展示类型，
 * 供 FlowSection / KnowledgeFlowCard / FlowView 共享，避免视图层直接依赖 generated schema 字段名。
 */

export interface FlowMetric {
  label: string
  value: number
  accent?: boolean
}

export interface FlowStep {
  label: string
  state: 'done' | 'current' | 'todo'
}

export interface FlowCandidate {
  title: string
  summary: string
  tags: string[]
  actions: string[]
}

export interface FlowDomain {
  name: string
  count: number
  color: string
}

export interface FlowRecentItem {
  id: string
  title: string
  type: 'conversation' | 'knowledge'
  /** ISO 时间串；由展示层格式化 */
  updatedAt: string
}

export interface FlowData {
  focus: {
    eyebrow: string
    title: string
    description: string
    metrics: FlowMetric[]
  }
  flow: {
    label: string
    statusText: string
    countText: string
    active: boolean
    steps: FlowStep[]
  }
  candidate: FlowCandidate | null
  domains: FlowDomain[]
  /** 最近活跃：最近更新的对话/知识条目（各取前几条，混合按 updatedAt 倒序） */
  recent: FlowRecentItem[]
  /** 索引进度：知识条目里已索引 / 处理中 / 失败 的数量 */
  indexed: number
  processing: number
  failed: number
}
