<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { RetrievedSource } from '../api/types/conversation'

const props = defineProps<{
  ragStatus?: string | null
  sources?: RetrievedSource[] | null
}>()

const router = useRouter()
const showDiagnostics = ref(false)

const RAG_STATUS_TEXT: Record<string, string> = {
  NOT_AVAILABLE: '知识库不可用',
  NOT_NEEDED: '无需检索知识库',
  USED: '已使用个人知识',
  NO_RELEVANT_CONTEXT: '未找到相关内容',
  DEGRADED: '检索降级',
}

const statusText = computed(() => {
  if (!props.ragStatus) return null
  return RAG_STATUS_TEXT[props.ragStatus] ?? props.ragStatus
})

const hasSources = computed(() => (props.sources ?? []).length > 0)

const citedCount = computed(() => (props.sources ?? []).filter((s) => s.cited).length)

function openItem(itemId?: string) {
  if (itemId) {
    void router.push(`/knowledge-items/${itemId}`)
  }
}
</script>

<template>
  <div v-if="statusText" class="sources-panel">
    <div class="status-line">
      <span class="status-badge" :class="(ragStatus ?? '').toLowerCase()">{{ statusText }}</span>
      <el-button
        v-if="hasSources"
        size="small"
        text
        class="diag-toggle"
        @click="showDiagnostics = !showDiagnostics"
      >
        {{ showDiagnostics ? '收起来源' : `来源 ${citedCount}/${(sources ?? []).length}` }}
      </el-button>
    </div>

    <div v-if="showDiagnostics && hasSources" class="sources-list">
      <div v-for="(s, i) in sources" :key="s.sourceId" class="source-item">
        <div class="source-head">
          <span class="source-idx">[S{{ i + 1 }}]</span>
          <a class="source-title" @click.prevent="openItem(s.itemId)">{{ s.itemTitle }}</a>
          <el-tag v-if="s.cited" size="small" type="success">已引用</el-tag>
          <el-tag v-else size="small" type="info">检索</el-tag>
        </div>
        <div class="source-snippet">{{ s.snippet }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sources-panel {
  margin-top: 8px;
  font-size: 0.85rem;
}
.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  color: #606266;
  background: #f0f2f5;
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
.diag-toggle {
  padding: 0;
}
.sources-list {
  margin-top: 6px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px;
  background: #fff;
}
.source-item {
  padding: 4px 0;
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
  cursor: pointer;
  color: #409eff;
  text-decoration: underline;
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
