<script setup lang="ts">
// 会话记忆面板：从当前消息历史近似展示"参与上下文"（后端不暴露 Memory 摘要，见 BACKEND_MISSING）。
import { computed } from 'vue'
import type { MessageResponse } from '../api/types/conversation'

const props = defineProps<{
  messages: MessageResponse[]
}>()

const MAX_CONTEXT_TURNS = 10

/** 参与上下文：完整 active 的 User/Assistant 轮次（排除 failed/cancelled）。 */
const contextTurnCount = computed(
  () =>
    props.messages.filter((m) => {
      if (m.role !== 'USER' && m.role !== 'ASSISTANT') return false
      if (m.generationStatus === 'FAILED' || m.generationStatus === 'CANCELLED') return false
      return true
    }).length,
)

const isActive = computed(() => contextTurnCount.value > 0)

const contextMeterWidth = computed(
  () => `${Math.min(100, Math.round((contextTurnCount.value / MAX_CONTEXT_TURNS) * 100))}%`,
)
</script>

<template>
  <section class="inspector-section memory-panel">
    <div class="inspector-head">
      <div>
        <span class="eyebrow">短期上下文</span>
        <h3>会话记忆</h3>
      </div>
      <span class="memory-switch" :class="{ on: isActive }">{{ isActive ? 'ON' : 'OFF' }}</span>
    </div>
    <template v-if="isActive">
      <p class="memory-quote">
        当前会话有 {{ contextTurnCount }} 条完整消息参与上下文。Memory
        由后端在每次生成时自动注入，前端不直接读取记忆内容。
      </p>
      <div class="memory-meter"><span :style="{ width: contextMeterWidth }" /></div>
      <div class="memory-foot">
        <span>{{ contextTurnCount }} 条消息参与上下文</span>
        <b>记忆开关</b>
      </div>
    </template>
    <p v-else class="memory-quote">
      还没有可用的会话上下文。发送一条消息后，Memory 会自动开始累积。
    </p>
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
  color: #c7d7c8;
  font-weight: 900;
}
.memory-switch {
  font-size: 8px;
  font-weight: 900;
  border: 1px solid var(--kf-ink);
  border-radius: 999px;
  padding: 4px 7px;
  background: var(--kf-white);
  color: var(--kf-muted);
}
.memory-switch.on {
  background: var(--kf-green-soft);
  color: var(--kf-green);
  border-color: #96aa99;
}
.memory-panel {
  background: var(--kf-green);
  color: var(--kf-paper);
  padding: 15px;
  border-radius: 17px;
  box-shadow: 4px 4px 0 var(--kf-yellow);
}
.memory-panel .inspector-head h3 {
  color: #fff;
}
.memory-quote {
  font-size: 9px;
  line-height: 1.7;
  color: #d8e2d8;
  padding: 10px 0 12px;
  margin: 0;
}
.memory-meter {
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  overflow: hidden;
}
.memory-meter span {
  display: block;
  height: 100%;
  background: var(--kf-yellow);
  border-radius: inherit;
}
.memory-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 7px;
  color: #c3d2c4;
  font-weight: 800;
}
.memory-foot b {
  color: var(--kf-yellow);
}
</style>
