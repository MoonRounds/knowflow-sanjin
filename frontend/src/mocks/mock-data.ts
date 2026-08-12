/**
 * /flow 学习流首页的集中 mock 数据。
 *
 * ⚠️ MOCK：仅用于视觉迁移，未来应被真实 API 替换，且替换点集中在模块导出处。
 * 对应后端能力的映射：
 *   - focus            → 无对应 API（学习焦点聚合，BACKEND_MISSING）
 *   - flow.steps       → 无对应 API（知识处理状态机展示，可来自 ProcessingTask/ExtractionTask 推导）
 *   - candidate        → GET /candidates（知识候选审核）
 *   - domains          → GET /knowledge-bases（+ itemCount，BACKEND_MISSING）
 *   - memory           → 无对应 API（Chat Memory，BACKEND_MISSING）
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

export interface FlowMemory {
  title: string
  text: string
  hint: string
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
    countText: string
    steps: FlowStep[]
  }
  candidate: FlowCandidate
  domains: FlowDomain[]
  memory: FlowMemory
}

/** 集中 mock：全部 /flow 首页展示数据（标注 MOCK，非真实 API）。 */
export const mockFlowData: FlowData = {
  focus: {
    eyebrow: '当前学习焦点 / 线程安全',
    title: '先聊懂，\n再把真正有用的留下。',
    description:
      '你正在学习 ConcurrentHashMap。知流不替你囤积答案，而是把对话里真正值得保留的理解，变成以后还能再次调用的个人知识。',
    metrics: [
      { label: '次追问', value: 12 },
      { label: '条候选', value: 3 },
      { label: '条已沉淀', value: 1, accent: true },
    ],
  },
  flow: {
    label: '这条知识正在流动',
    countText: '03 / 06',
    steps: [
      { label: '对话', state: 'done' },
      { label: '识别价值', state: 'done' },
      { label: '知识提炼', state: 'current' },
      { label: '你的确认', state: 'todo' },
      { label: '沉淀', state: 'todo' },
      { label: '再次调用', state: 'todo' },
    ],
  },
  candidate: {
    title: 'ConcurrentHashMap 从分段锁到桶级并发',
    summary:
      'JDK 7 通过 Segment + ReentrantLock 提升并发度；JDK 8 取消 Segment，以 CAS、桶头 synchronized 与更直接的数据结构降低额外层级。',
    tags: ['Java 并发', 'ConcurrentHashMap', 'JDK 7 / 8'],
    actions: ['确认沉淀', '先修改'],
  },
  domains: [
    { name: 'Java 与 JVM', count: 128, color: 'var(--kf-hot)' },
    { name: 'Redis', count: 64, color: 'var(--kf-yellow)' },
    { name: 'Spring 生态', count: 91, color: 'var(--kf-green-soft)' },
    { name: 'RAG 实践', count: 37, color: 'var(--kf-blue)' },
  ],
  memory: {
    title: '正在记住什么？',
    text: '当前主题围绕 ConcurrentHashMap 的线程安全机制，用户已从 JDK 8 追问到 JDK 7，下一步可能继续比较实现细节或版本演进原因。',
    hint: '仅用于本次对话连续性 · 不等于长期知识',
  },
}
