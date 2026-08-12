<script setup lang="ts">
// 可调用知识域面板：来自真实 GET /knowledge-bases（仅 enabled）。选中态由父组件管理。
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'

const props = defineProps<{
  domains: KnowledgeBaseResponse[]
  selectedIds?: string[]
}>()

const emit = defineEmits<{ toggle: [id: string] }>()

function isOn(id: string): boolean {
  return (props.selectedIds ?? []).includes(id)
}
</script>

<template>
  <section class="inspector-section knowledge-panel">
    <div class="inspector-head">
      <div>
        <span class="eyebrow">长期知识</span>
        <h3>可调用的知识域</h3>
      </div>
    </div>
    <div v-if="domains.length" class="knowledge-chips">
      <button
        v-for="d in domains"
        :key="d.id"
        :class="{ on: isOn(d.id!) }"
        @click="emit('toggle', d.id!)"
      >
        {{ d.name }}
      </button>
    </div>
    <p v-else class="empty-hint">还没有知识库。先去"知识库"创建一个。</p>
  </section>
</template>

<style scoped>
.inspector-section {
  margin-bottom: 24px;
}
.inspector-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.inspector-head h3 {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 900;
  letter-spacing: -0.3px;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--kf-hot);
  font-weight: 900;
}
.knowledge-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.knowledge-chips button {
  border: 1px solid var(--kf-line);
  background: var(--kf-white);
  border-radius: 999px;
  padding: 7px 9px;
  font-size: 8px;
  font-weight: 900;
  cursor: pointer;
  color: var(--kf-ink);
}
.knowledge-chips button.on {
  border-color: var(--kf-ink);
  box-shadow: 2px 2px 0 var(--kf-ink);
}
.empty-hint {
  font-size: 9px;
  color: var(--kf-muted);
  margin: 0;
}
</style>
