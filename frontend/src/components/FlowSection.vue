<script setup lang="ts">
// 当前学习焦点区：大字号粗标题 + 描述 + 指标胶囊（来自设计稿 .focus）。
import type { FlowData } from '../mocks/mock-data'

defineProps<{ focus: FlowData['focus'] }>()
</script>

<template>
  <section class="focus">
    <div class="eyebrow">{{ focus.eyebrow }}</div>
    <h2>{{ focus.title }}</h2>
    <p>{{ focus.description }}</p>
    <div class="focus-meta">
      <span v-for="m in focus.metrics" :key="m.label">
        <b>{{ m.value }}</b> {{ m.label }}
      </span>
      <em>不是收藏，是回流。</em>
    </div>
  </section>
</template>

<style scoped>
.focus {
  border-top: 5px solid var(--kf-ink);
  padding-top: 17px;
  position: relative;
  min-height: 210px;
}
.focus::after {
  content: '01';
  position: absolute;
  right: 8px;
  top: 8px;
  font-size: 74px;
  line-height: 1;
  font-weight: 900;
  color: rgba(25, 24, 21, 0.045);
  letter-spacing: -6px;
  pointer-events: none;
}
.eyebrow {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--kf-hot);
  font-weight: 900;
}
.focus h2 {
  font-size: var(--kf-text-display);
  line-height: 1.12;
  font-weight: 900;
  letter-spacing: -3px;
  margin: 12px 0 16px;
  max-width: 790px;
  white-space: pre-line;
}
.focus p {
  font-size: 14px;
  line-height: 1.9;
  color: var(--kf-muted);
  max-width: 680px;
  margin: 0;
  font-weight: 600;
}
.focus-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 20px;
}
.focus-meta span {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  border: 1px solid var(--kf-ink);
  background: var(--kf-white);
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 800;
  box-shadow: 2px 2px 0 rgba(25, 24, 21, 0.09);
}
.focus-meta b {
  font-size: 14px;
  font-weight: 900;
}
.focus-meta em {
  font-style: normal;
  font-size: 10px;
  font-weight: 900;
  color: var(--kf-hot);
  margin-left: 4px;
}
</style>
