/**
 * /flow 学习流首页数据：从真实 API 拉取并推导展示数据（不再使用 mock）。
 *
 * 对应关系：
 *   - hero 指标   → 候选数（GET /candidates?status=PENDING）、知识库数（GET /knowledge-bases）、
 *                   已沉淀笔记数（GET /knowledge-items）、任务数（GET /processing-tasks）
 *   - 流程卡      → 由最近一个 PROCESSING 类任务（INDEXING/EXTRACTION/PARSING）推导"知识正在流动"
 *   - 候选卡      → GET /candidates?status=PENDING 最新一条
 *   - 知识域      → GET /knowledge-bases 列表（无 itemCount 时按有无数据展示，不再伪造数量）
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { listCandidates } from '../api/extraction'
import { listConversations } from '../api/conversations'
import { listKnowledgeBases } from '../api/knowledge-bases'
import { listKnowledgeItems } from '../api/knowledge-items'
import { listProcessingTasks } from '../api/processing-tasks'
import type { FlowData, FlowRecentItem } from '../mocks/mock-data'
import type { ProcessingTaskResponse } from '../api/types/processing-task'
import type { ConversationResponse } from '../api/types/conversation'

const FLOW_STEPS = [
  { label: '对话', state: 'done' as const },
  { label: '识别价值', state: 'done' as const },
  { label: '知识提炼', state: 'current' as const },
  { label: '你的确认', state: 'todo' as const },
  { label: '沉淀', state: 'todo' as const },
  { label: '再次调用', state: 'todo' as const },
]

/** 处理任务 → 流程卡当前阶段的语义文案。 */
function taskStep(task: ProcessingTaskResponse | undefined): { label: string; step: string } {
  if (!task) return { label: '等待新输入', step: '对话' }
  const type = task.taskType ?? ''
  if (type.includes('EXTRACTION')) return { label: '识别对话中的价值', step: '识别价值' }
  if (type.includes('INDEX')) return { label: '正在写入个人知识索引', step: '知识提炼' }
  if (type.includes('PARSE')) return { label: '正在解析上传文件', step: '知识提炼' }
  return { label: '等待新输入', step: '对话' }
}

function isRunning(task: ProcessingTaskResponse): boolean {
  return task.status === 'PROCESSING' || task.status === 'PENDING'
}

/** 把「最近更新的对话 / 知识条目」混合排序，取最近 N 条作为侧栏「最近活跃」。 */
function buildRecent(
  conversations: ConversationResponse[],
  items: Array<{ id: string; title: string; updatedAt: string }>,
): FlowRecentItem[] {
  const convs: FlowRecentItem[] = conversations.map((c) => ({
    id: c.id!,
    title: c.title ?? '未命名对话',
    type: 'conversation' as const,
    updatedAt: c.updatedAt ?? c.createdAt ?? '',
  }))
  const knows: FlowRecentItem[] = items.map((i) => ({
    id: i.id,
    title: i.title,
    type: 'knowledge' as const,
    updatedAt: i.updatedAt,
  }))
  return [...convs, ...knows]
    .filter((r) => r.updatedAt)
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, 6)
}

/** 按 indexStatus 统计知识条目：已索引 / 处理中 / 失败。 */
function indexCounts(items: Array<{ indexStatus: string }>): {
  indexed: number
  processing: number
  failed: number
} {
  let indexed = 0
  let processing = 0
  let failed = 0
  for (const it of items) {
    if (it.indexStatus === 'INDEXED') indexed += 1
    else if (it.indexStatus === 'FAILED') failed += 1
    else processing += 1 // PENDING / PROCESSING
  }
  return { indexed, processing, failed }
}

export function useFlowData() {
  const loading = ref(true)
  let refreshTimer: number | undefined
  let refreshing = false
  const data = ref<FlowData>({
    focus: { eyebrow: '当前学习概览', title: '', description: '', metrics: [] },
    flow: {
      label: '等待下一条知识流',
      statusText: '有新的提炼或索引任务时，这里会实时更新。',
      countText: '— / 06',
      active: false,
      steps: FLOW_STEPS.map((step) => ({ ...step, state: 'todo' })),
    },
    candidate: null,
    domains: [],
    recent: [],
    indexed: 0,
    processing: 0,
    failed: 0,
  })

  async function load(initial = false) {
    if (refreshing) return
    refreshing = true
    if (initial) loading.value = true
    try {
      const [candidates, kbs, items, tasks, conversations] = await Promise.all([
        listCandidates({ status: 'PENDING' }),
        listKnowledgeBases(),
        listKnowledgeItems(),
        listProcessingTasks(),
        listConversations(),
      ])

      const pending = candidates.items ?? []
      const pendingCount = candidates.total ?? pending.length
      const domains = kbs
        .filter((kb) => kb.enabled)
        .map((kb) => ({ name: kb.name, count: 0, color: 'var(--kf-green)' }))

      const runningTasks = (tasks ?? [])
        .filter(isRunning)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      const activeTask = runningTasks[0]
      const { label: statusText, step } = taskStep(activeTask)

      const currentIdx = FLOW_STEPS.findIndex((s) => s.label === step)
      const steps = activeTask
        ? FLOW_STEPS.map((s, i) =>
            i < currentIdx
              ? { ...s, state: 'done' as const }
              : i === currentIdx
                ? { ...s, state: 'current' as const }
                : { ...s, state: 'todo' as const },
          )
        : FLOW_STEPS.map((s) => ({ ...s, state: 'todo' as const }))
      const countText = activeTask ? `${String(currentIdx + 1).padStart(2, '0')} / 06` : '— / 06'

      const firstPending = pending[0]
      const candidate = firstPending
        ? {
            title: firstPending.aiTitle ?? '未命名候选',
            summary: firstPending.aiSummary ?? firstPending.aiReason ?? '',
            tags: firstPending.aiTags ?? [],
            actions: ['确认沉淀', '先修改'],
          }
        : null

      const kbCount = domains.length
      const itemsCount = (items ?? []).length
      const taskCount = (tasks ?? []).length
      const idx = indexCounts(items ?? [])

      data.value = {
        focus: {
          eyebrow: '当前学习概览',
          title: '先聊懂，\n再把真正有用的留下。',
          description:
            pendingCount || kbCount || itemsCount || taskCount
              ? '你的学习正在被记录：对话里的价值会沉淀成可再次调用的个人知识。'
              : '去 AI 对话里聊聊正在学的东西，知流会把值得保留的理解变成个人知识。',
          metrics: [
            { label: '条待沉淀', value: pendingCount },
            { label: '个知识库', value: kbCount },
            { label: '条已沉淀', value: itemsCount, accent: true },
            { label: '个处理任务', value: taskCount },
          ],
        },
        flow: {
          label: activeTask ? '这条知识正在流动' : '等待下一条知识流',
          statusText: activeTask ? statusText : '有新的提炼或索引任务时，这里会实时更新。',
          countText,
          active: !!activeTask,
          steps,
        },
        candidate,
        domains,
        recent: buildRecent(conversations ?? [], items ?? []),
        ...idx,
      }
    } catch {
      data.value = {
        focus: {
          eyebrow: '当前学习概览',
          title: '先聊懂，\n再把真正有用的留下。',
          description: '暂时无法读取你的学习数据，请稍后刷新重试。',
          metrics: [],
        },
        flow: {
          label: '等待下一条知识流',
          statusText: '暂时无法读取处理进度，请稍后重试。',
          countText: '— / 06',
          active: false,
          steps: FLOW_STEPS.map((step) => ({ ...step, state: 'todo' })),
        },
        candidate: null,
        domains: [],
        recent: [],
        indexed: 0,
        processing: 0,
        failed: 0,
      }
    } finally {
      if (initial) loading.value = false
      refreshing = false
    }
  }

  function refreshWhenVisible() {
    if (document.visibilityState !== 'visible') return
    if (refreshTimer) window.clearTimeout(refreshTimer)
    void refreshAndSchedule()
  }

  async function refreshAndSchedule() {
    if (document.visibilityState !== 'visible') {
      refreshTimer = undefined
      return
    }
    await load(false)
    refreshTimer = window.setTimeout(refreshAndSchedule, data.value.flow.active ? 3000 : 15000)
  }

  onMounted(() => {
    void load(true).then(() => {
      refreshTimer = window.setTimeout(refreshAndSchedule, data.value.flow.active ? 3000 : 15000)
    })
    document.addEventListener('visibilitychange', refreshWhenVisible)
  })
  onUnmounted(() => {
    if (refreshTimer) window.clearTimeout(refreshTimer)
    document.removeEventListener('visibilitychange', refreshWhenVisible)
  })

  return { loading, data }
}
