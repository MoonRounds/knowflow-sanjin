<script setup lang="ts">
defineProps<{
  title: string
  description: string
  actionLabel?: string
  wide?: boolean
}>()

defineEmits<{
  action: []
}>()
</script>

<template>
  <section class="kf-empty" :class="{ 'kf-empty--wide': wide }" role="status">
    <svg class="kf-empty-art" viewBox="0 0 260 170" fill="none" aria-hidden="true">
      <path class="flow-line" d="M28 52C68 52 70 86 108 86S150 55 190 55" />
      <path class="flow-line flow-line-short" d="M64 121c28 0 34 17 60 17" />
      <rect class="chat-shadow" x="22" y="25" width="86" height="58" rx="20" />
      <path
        class="chat-card"
        d="M17 18h86a14 14 0 0 1 14 14v35a14 14 0 0 1-14 14H55L37 96V81H17A14 14 0 0 1 3 67V32a14 14 0 0 1 14-14Z"
      />
      <path class="chat-line" d="M28 42h63M28 57h42" />
      <path class="paper-back" d="m151 38 65 14-16 78-65-14z" />
      <rect class="paper-shadow" x="139" y="48" width="77" height="92" rx="14" />
      <rect class="paper-card" x="130" y="39" width="77" height="92" rx="14" />
      <circle class="paper-dot" cx="151" cy="62" r="6" />
      <path class="paper-line" d="M165 62h23M147 83h41M147 99h31" />
      <path class="spark" d="m226 24 3 8 8 3-8 3-3 8-3-8-8-3 8-3 3-8Z" />
    </svg>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <button v-if="actionLabel" type="button" class="kf-empty-action" @click="$emit('action')">
      {{ actionLabel }} <span aria-hidden="true">→</span>
    </button>
  </section>
</template>

<style scoped>
.kf-empty {
  box-sizing: border-box;
  width: min(560px, 100%);
  margin: 42px auto;
  padding: 30px 24px 34px;
  text-align: center;
  border: 1px solid var(--kf-line);
  border-radius: var(--kf-radius-lg);
  background: color-mix(in srgb, var(--kf-white) 82%, transparent);
}
.kf-empty--wide {
  width: 100%;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.kf-empty-art {
  width: 230px;
  max-width: 82%;
  height: auto;
  overflow: visible;
}
.flow-line {
  stroke: var(--kf-hot);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-dasharray: 5 8;
}
.flow-line-short {
  stroke: var(--kf-ink);
  opacity: 0.28;
}
.chat-shadow,
.paper-shadow {
  fill: var(--kf-red);
}
.chat-card,
.paper-card {
  fill: var(--kf-white);
  stroke: var(--kf-ink);
  stroke-width: 3;
}
.chat-line,
.paper-line {
  stroke: var(--kf-ink);
  stroke-width: 4;
  stroke-linecap: round;
}
.paper-back {
  fill: var(--kf-paper-2);
  stroke: var(--kf-ink);
  stroke-width: 2;
}
.paper-dot,
.spark {
  fill: var(--kf-hot);
  stroke: var(--kf-ink);
  stroke-width: 2;
}
.kf-empty h3 {
  margin: 2px 0 8px;
  color: var(--kf-ink);
  font-size: 18px;
  font-weight: 900;
  letter-spacing: -0.3px;
}
.kf-empty p {
  max-width: 440px;
  margin: 0 auto;
  color: var(--kf-muted);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.75;
}
.kf-empty-action {
  min-height: var(--kf-touch-min);
  margin-top: 20px;
  padding: 0 20px;
  border: 1px solid var(--kf-ink);
  border-radius: var(--kf-radius-md);
  background: var(--kf-ink);
  box-shadow: 3px 3px 0 var(--kf-red);
  color: var(--kf-paper);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 900;
}
.kf-empty-action:hover {
  transform: translateY(-1px);
}
</style>
