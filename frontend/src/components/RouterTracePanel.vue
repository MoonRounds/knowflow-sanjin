<script setup lang="ts">
// 知识路由轨迹面板：展示当前 assistant 消息的路由过程（实时 SSE 数据；历史消息用 ragStatus 近似）。
import { computed } from 'vue'
import type { RouterDiagnostic } from '../composables/useChatStream'
import { deriveRouterSteps } from '../utils/chat-workspace'

const props = defineProps<{
  router?: RouterDiagnostic | null
  ragStatus?: string | null
}>()

const steps = computed(() => deriveRouterSteps(props.router ?? undefined, props.ragStatus))
</script>

<template>
  <section class="inspector-section route-trace">
    <div class="inspector-head">
      <div>
        <span class="eyebrow">本轮怎么回答</span>
        <h3>知识路由轨迹</h3>
      </div>
      <span class="status-dot">自动</span>
    </div>
    <div class="trace-list">
      <div
        v-for="(step, i) in steps"
        :key="step.label"
        class="trace"
        :class="{ skipped: step.state === 'skipped' }"
      >
        <i>{{ i + 1 }}</i>
        <div>
          <b>{{ step.label }}</b>
          <span>{{ step.detail }}</span>
        </div>
        <em>{{ step.state === 'skipped' ? '—' : '✓' }}</em>
      </div>
    </div>
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
.status-dot {
  font-size: 8px;
  font-weight: 900;
  border: 1px solid var(--kf-ink);
  border-radius: 999px;
  padding: 4px 7px;
  background: var(--kf-white);
}
.trace-list {
  border-top: 2px solid var(--kf-ink);
}
.trace {
  display: grid;
  grid-template-columns: 25px 1fr auto;
  gap: 9px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed var(--kf-line);
}
.trace i {
  width: 23px;
  height: 23px;
  border-radius: 8px;
  background: var(--kf-ink);
  color: var(--kf-paper);
  display: grid;
  place-items: center;
  font-size: 8px;
  font-style: normal;
  font-weight: 900;
}
.trace b {
  display: block;
  font-size: 9px;
  font-weight: 900;
}
.trace span {
  display: block;
  font-size: 8px;
  color: var(--kf-muted);
  font-weight: 700;
  margin-top: 2px;
  line-height: 1.4;
}
.trace em {
  font-style: normal;
  font-size: 10px;
  font-weight: 900;
  color: var(--kf-green);
}
.trace.skipped i {
  background: var(--kf-paper-2);
  color: var(--kf-muted);
}
.trace.skipped em {
  color: var(--kf-muted);
}
</style>
