<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { RetrievedSource } from '../api/types/conversation'
import { ragStatusText } from '../utils/rag'
import { sourceTypeLabel } from '../utils/sourceType'

const props = defineProps<{
  ragStatus?: string | null
  sources?: RetrievedSource[] | null
  /** 展开状态由外部控制（正文 [Sx] 点击或面板 badge）。 */
  expanded?: boolean
  /** 外部指定高亮的来源序号（1-based，[Sx] 编号）。 */
  highlightIndex?: number
}>()

const emit = defineEmits<{ toggle: [] }>()

const router = useRouter()

const statusText = computed(() => {
  if (props.ragStatus === 'NOT_AVAILABLE' || props.ragStatus === 'NOT_NEEDED') return null
  return ragStatusText(props.ragStatus)
})

const hasSources = computed(() => (props.sources ?? []).length > 0)

const citedCount = computed(() => (props.sources ?? []).filter((s) => s.cited).length)

const summaryText = computed(() => {
  if (hasSources.value) {
    return `来源个人知识库 ${citedCount.value}/${(props.sources ?? []).length}`
  }
  return statusText.value
})

function openDocument(documentId?: string) {
  if (documentId) {
    void router.push(`/documents/${documentId}`)
  }
}

function openKnowledgeBase(id?: string) {
  if (id) {
    void router.push(`/knowledge-bases/${id}`)
  }
}
</script>

<template>
  <div v-if="summaryText" class="sources-panel">
    <div class="status-line">
      <button
        type="button"
        class="status-badge"
        :class="[(ragStatus ?? '').toLowerCase(), { interactive: hasSources }]"
        :disabled="!hasSources"
        @click="hasSources && emit('toggle')"
      >
        {{ expanded && hasSources ? '收起个人知识' : summaryText }}
      </button>
    </div>

    <div v-if="expanded && hasSources" class="sources-list">
      <div
        v-for="(s, i) in sources"
        :key="s.sourceId"
        class="source-item"
        :class="{ highlighted: highlightIndex === i + 1 }"
      >
        <div class="source-head">
          <span class="source-idx">[S{{ i + 1 }}]</span>
          <button type="button" class="source-title" @click="openDocument(s.documentId)">
            {{ s.documentTitle }}
          </button>
          <el-tag v-if="s.cited" size="small" type="success">已引用</el-tag>
          <el-tag v-else size="small" type="info">检索</el-tag>
        </div>
        <div class="source-meta">
          <button
            v-if="s.knowledgeBaseId"
            type="button"
            class="source-kb-link"
            @click="openKnowledgeBase(s.knowledgeBaseId)"
          >
            {{ s.knowledgeBaseName ?? '未知知识库' }}
          </button>
          <span v-if="sourceTypeLabel(s.sourceType)" class="source-type-tag">
            {{ sourceTypeLabel(s.sourceType) }}
          </span>
        </div>
        <div class="source-snippet">{{ s.snippet }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sources-panel {
  min-width: 0;
  font-size: 0.85rem;
}
.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-badge {
  min-height: 34px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 0.75rem;
  color: #606266;
  background: #f0f2f5;
  font-family: inherit;
  font-weight: 800;
}
.status-badge.used {
  color: #67c23a;
  background: #f0f9eb;
}
.status-badge.degraded {
  color: #e6a23c;
  background: #fdf6ec;
}
.status-badge.no_relevant_context {
  color: #909399;
  background: #f4f4f5;
}
.status-badge.not_needed {
  color: #909399;
  background: #f4f4f5;
}
.status-badge.not_available {
  color: #909399;
  background: #f4f4f5;
}
.status-badge.interactive {
  cursor: pointer;
  border-color: var(--kf-green);
}
.status-badge:disabled {
  cursor: default;
}
.sources-list {
  margin-top: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px;
  background: #fff;
}
.source-item {
  padding: 4px 0;
  border-radius: 4px;
}
.source-item.highlighted {
  background: #f0f9eb;
  outline: 1px solid #67c23a;
}
.source-item + .source-item {
  border-top: 1px solid #f0f2f5;
}
.source-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.source-idx {
  font-weight: 600;
  color: #409eff;
}
.source-title {
  appearance: none;
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  color: #409eff;
  text-decoration: underline;
  font: inherit;
  font-size: inherit;
  text-align: left;
}
.source-title:focus-visible {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
.source-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}
.source-kb-link {
  appearance: none;
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  color: #909399;
  text-decoration: underline;
  font: inherit;
  font-size: 0.75rem;
  text-align: left;
}
.source-kb-link:hover {
  color: #409eff;
}
.source-type-tag {
  font-size: 0.7rem;
  color: #909399;
  border: 1px solid #e4e7ed;
  border-radius: 999px;
  padding: 0 6px;
  line-height: 1.5;
}
.source-snippet {
  color: #909399;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
