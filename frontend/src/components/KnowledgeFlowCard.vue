<script setup lang="ts">
// 知识流动进度卡片：展示一条知识从"对话"到"再次调用"的处理阶段。
import type { FlowData } from '../mocks/mock-data'

defineProps<{ flow: FlowData['flow'] }>()
</script>

<template>
  <section class="flow" :class="{ active: flow.active }" aria-live="polite">
    <div class="flowtop">
      <h3>{{ flow.label }}</h3>
      <span class="flowcount">{{ flow.countText }}</span>
    </div>
    <p class="flowstatus">{{ flow.statusText }}</p>
    <div class="flowline">
      <div v-for="step in flow.steps" :key="step.label" class="step">
        <div class="stepbar" :class="step.state" />
        <span>{{ step.label }}</span>
      </div>
    </div>
    <div class="flowfoot">聊过的不散，学会的留下。</div>
  </section>
</template>

<style scoped>
.flow {
  border: 1px solid var(--kf-ink);
  background: var(--kf-paper-2);
  color: var(--kf-ink);
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 205px;
  box-shadow: 7px 7px 0 var(--kf-ink);
  border-radius: 22px 5px 22px 5px;
  transform: rotate(0.5deg);
  transition:
    color var(--kf-duration-base) var(--kf-ease),
    background var(--kf-duration-base) var(--kf-ease);
}
.flow.active {
  background: var(--kf-red);
  color: var(--kf-white);
}
.flowtop {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.flow h3 {
  font-size: 15px;
  margin: 0;
  font-weight: 900;
  letter-spacing: -0.3px;
}
.flowcount {
  font-size: 10px;
  font-weight: 900;
  border: 1px solid var(--kf-ink);
  border-radius: 999px;
  padding: 5px 8px;
  background: rgba(255, 255, 255, 0.35);
}
.flow.active .flowcount {
  border-color: var(--kf-white);
  background: rgba(255, 255, 255, 0.13);
}
.flowstatus {
  min-height: 1.6em;
  margin: 14px 0 0;
  font-size: 11px;
  font-weight: 800;
  opacity: 0.82;
}
.flowline {
  display: flex;
  gap: 4px;
  align-items: flex-start;
  margin-top: 26px;
}
.step {
  flex: 1;
  min-width: 0;
}
.stepbar {
  height: 7px;
  border-radius: 999px;
  background: rgba(23, 23, 19, 0.18);
  margin-bottom: 8px;
  position: relative;
  overflow: hidden;
}
.stepbar.done::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--kf-ink);
}
.flow.active .stepbar.done::after {
  background: var(--kf-white);
}
.stepbar.current::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 58%;
  background: var(--kf-ink);
}
.step span {
  font-size: 9px;
  white-space: nowrap;
  color: #3f3b2f;
  font-weight: 800;
}
.flow.active .step span {
  color: var(--kf-white);
}
.flowfoot {
  font-size: 11px;
  font-weight: 900;
  margin-top: 17px;
  letter-spacing: 0.03em;
}
</style>
