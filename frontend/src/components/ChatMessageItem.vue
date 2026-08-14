<script setup lang="ts">
// 单条聊天消息：computed 缓存 Markdown 渲染，避免每条 delta 到达时全量重渲染历史消息。
// [Sx] 引用交互（G32）：悬停 el-tooltip 预览该来源 snippet，点击展开来源面板并高亮对应项。
import { computed, nextTick, ref } from 'vue'
import type { RetrievedSource, MessageResponse } from '../api/types/conversation'
import type { RouterDiagnostic } from '../composables/useChatStream'
import { escapeHtml, renderMarkdown } from '../utils/markdown'
import MessageSourcesPanel from './MessageSourcesPanel.vue'
import { useCodeBlockCopy } from '../composables/useCodeBlockCopy'

const props = defineProps<{
  msg: MessageResponse
  router?: RouterDiagnostic | null
}>()

const emit = defineEmits<{ stop: []; regenerate: [msg: MessageResponse] }>()

const { handleClick: handleCodeBlockClick } = useCodeBlockCopy()

/** 按消息内容缓存渲染：同一条消息的 content 未变则不重新解析 markdown。 */
const contentHtml = computed(() =>
  props.msg.role === 'USER'
    ? escapeHtml(props.msg.content ?? '')
    : renderMarkdown(props.msg.content ?? '', props.msg.sources),
)

const panelExpanded = ref(false)
const highlightIndex = ref<number | undefined>(undefined)
const panelEl = ref<HTMLElement | null>(null)

function togglePanel() {
  panelExpanded.value = !panelExpanded.value
}

/** 点击正文 [Sx]：展开来源面板、滚动到对应项并短暂高亮。 */
function openCite(index: number) {
  panelExpanded.value = true
  highlightIndex.value = index
  void nextTick(() => {
    panelEl.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
  window.setTimeout(() => {
    if (highlightIndex.value === index) {
      highlightIndex.value = undefined
    }
  }, 3000)
}

const hoverEl = ref<HTMLElement | null>(null)
const hoverSource = ref<{ index: number; source: RetrievedSource } | null>(null)
const tooltipVisible = ref(false)

/** 定位事件目标命中的引用 span 并解析来源（悬停/点击事件委托共用）。 */
function findCite(
  event: MouseEvent,
): { el: HTMLElement; index: number; source: RetrievedSource } | null {
  const el = (event.target as HTMLElement).closest('.kf-cite') as HTMLElement | null
  if (!el) return null
  const index = Number(el.dataset.sourceIndex)
  const source = props.msg.sources?.[index - 1]
  if (!source) return null
  return { el, index, source }
}

function onContentMouseover(event: MouseEvent) {
  const hit = findCite(event)
  if (!hit) {
    tooltipVisible.value = false
    return
  }
  hoverEl.value = hit.el
  hoverSource.value = { index: hit.index, source: hit.source }
  tooltipVisible.value = true
}

function onContentMouseleave() {
  tooltipVisible.value = false
}

function onContentClick(event: MouseEvent) {
  const hit = findCite(event)
  if (hit) {
    openCite(hit.index)
    return
  }
  // 非引用点击：交给代码块复制处理
  handleCodeBlockClick(event)
}
</script>

<template>
  <div class="message" :class="msg.role?.toLowerCase()">
    <div class="msg-role">
      {{ msg.role === 'USER' ? '你' : 'AI' }}
      <span v-if="msg.modelName" class="msg-model">{{ msg.modelName }}</span>
      <span
        v-if="msg.role === 'ASSISTANT' && msg.generationStatus === 'GENERATING'"
        class="msg-status"
      >
        生成中…
      </span>
    </div>
    <div
      v-if="msg.role !== 'ASSISTANT' || msg.content"
      class="msg-content"
      :aria-live="msg.generationStatus === 'GENERATING' ? 'polite' : undefined"
      @click="onContentClick"
      @mouseover="onContentMouseover"
      @mouseleave="onContentMouseleave"
      v-html="contentHtml"
    />
    <el-tooltip
      v-if="hoverSource"
      :virtual-ref="hoverEl"
      virtual-triggering
      :visible="tooltipVisible"
      placement="top"
      :show-after="0"
      :hide-after="0"
      :teleported="true"
    >
      <template #content>
        <div class="cite-tooltip">
          <div class="cite-tooltip-head">
            <span class="cite-tooltip-idx">[S{{ hoverSource.index }}]</span>
            <span class="cite-tooltip-title">{{ hoverSource.source.documentTitle }}</span>
          </div>
          <div v-if="hoverSource.source.chunkIndex !== undefined" class="cite-tooltip-chunk">
            chunk #{{ hoverSource.source.chunkIndex + 1 }}
          </div>
          <div class="cite-tooltip-snippet">{{ hoverSource.source.snippet }}</div>
        </div>
      </template>
    </el-tooltip>
    <div v-if="msg.generationStatus === 'FAILED'" class="msg-error">
      {{ msg.errorCode ?? '生成失败' }}
    </div>
    <div v-if="msg.generationStatus === 'CANCELLED'" class="msg-error">已取消</div>
    <div
      v-if="msg.role === 'ASSISTANT' && msg.generationStatus !== 'GENERATING'"
      class="message-footer"
    >
      <div ref="panelEl" class="sources-panel-wrap">
        <MessageSourcesPanel
          :rag-status="msg.ragStatus"
          :sources="msg.sources"
          :expanded="panelExpanded"
          :highlight-index="highlightIndex"
          @toggle="togglePanel"
        />
      </div>
      <el-button class="regenerate-action" size="small" text @click="emit('regenerate', msg)">
        重新生成
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.message {
  margin-bottom: 16px;
  max-width: 78%;
}
.message.user {
  width: fit-content;
  max-width: 72%;
  margin-left: auto;
  text-align: right;
}
.message.assistant {
  width: fit-content;
  max-width: 78%;
  margin-right: auto;
  text-align: left;
}
.msg-role {
  font-size: 0.75rem;
  font-weight: 900;
  color: var(--kf-muted);
  margin-bottom: 4px;
}
.msg-model {
  color: var(--kf-muted);
  margin-left: 6px;
  font-size: 0.7rem;
  font-weight: 700;
  border: 1px solid var(--kf-line);
  border-radius: 999px;
  padding: 1px 7px;
  background: var(--kf-paper);
}
.msg-status {
  margin-left: 6px;
  font-size: 0.7rem;
  font-weight: 900;
  color: var(--kf-hot);
}
.msg-content {
  background: var(--kf-paper-3);
  border: 1px solid var(--kf-line);
  color: var(--kf-ink);
  padding: 12px 15px;
  border-radius: 12px;
  word-break: break-word;
  line-height: 1.7;
  text-align: left;
}
.message.user .msg-content {
  background: var(--kf-ink);
  border-color: var(--kf-ink);
  color: var(--kf-paper);
  border-radius: 16px;
  border-top-right-radius: 6px;
  white-space: pre-wrap;
}
.msg-content :deep(p) {
  margin: 0.5em 0;
}
.msg-content :deep(p:first-child) {
  margin-top: 0;
}
.msg-content :deep(p:last-child) {
  margin-bottom: 0;
}
.msg-content :deep(pre) {
  background: #20221e;
  color: #f2f0e9;
  border-radius: 6px;
  padding: 10px 12px;
  overflow-x: auto;
  margin: 0.6em 0;
}
.msg-content :deep(code) {
  background: var(--kf-paper);
  border: 1px solid var(--kf-line);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.9em;
}
.msg-content :deep(pre code) {
  background: transparent;
  padding: 0;
}
.msg-content :deep(.kf-codeblock pre) {
  margin: 0;
  padding: 12px 14px;
  border-radius: 0;
  background: transparent;
}
.msg-content :deep(ul),
.msg-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.4em;
}
.msg-content :deep(blockquote) {
  border-left: 3px solid var(--kf-line);
  margin: 0.6em 0;
  padding-left: 12px;
  color: var(--kf-muted);
}
.msg-content :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
}
.msg-content :deep(th),
.msg-content :deep(td) {
  border: 1px solid var(--kf-line);
  padding: 5px 9px;
  font-size: 0.9em;
}
.msg-content :deep(a) {
  color: var(--kf-green);
  font-weight: 800;
}
.msg-content :deep(.kf-cite) {
  color: var(--kf-green);
  font-weight: 800;
  cursor: pointer;
  border-radius: 4px;
  padding: 0 1px;
}
.msg-content :deep(.kf-cite:hover) {
  background: rgba(74, 142, 110, 0.15);
}
.msg-content :deep(.kf-cite:focus-visible) {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
.msg-error {
  color: #f56c6c;
  font-size: 0.8rem;
  margin-top: 4px;
}
.message-footer {
  margin-top: 6px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.sources-panel-wrap {
  min-width: 0;
  flex: 1 1 auto;
}
.message-footer :deep(.el-button) {
  border-radius: 10px;
  font-weight: 800;
}
.regenerate-action {
  flex: 0 0 auto;
  margin-left: auto;
}
.cite-tooltip-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.cite-tooltip-idx {
  font-weight: 800;
  color: var(--kf-green);
}
.cite-tooltip-title {
  font-weight: 700;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cite-tooltip-chunk {
  color: var(--kf-muted);
  font-size: 0.75rem;
  margin-top: 2px;
}
.cite-tooltip-snippet {
  margin-top: 4px;
  max-width: 360px;
  max-height: 120px;
  overflow: hidden;
  color: #606266;
  font-size: 0.8rem;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
}
</style>
