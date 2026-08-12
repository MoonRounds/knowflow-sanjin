<script setup lang="ts">
// 根 Shell：左侧主 rail + 右侧（mast + 路由出口）。纸感视觉语言来自 tokens.css。
import { useRouter } from 'vue-router'
import AppRail from './components/AppRail.vue'
import AppMast from './components/AppMast.vue'

const router = useRouter()

function handleCreateTopic() {
  void router.push('/chat')
}
</script>

<template>
  <div class="shell">
    <AppRail />
    <section class="page">
      <AppMast @create-topic="handleCreateTopic" />
      <RouterView />
    </section>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--kf-rail-w) 1fr;
  background: var(--kf-paper);
  color: var(--kf-ink);
  font-family: var(--kf-font-round);
  font-weight: 600;
}
.page {
  min-width: 0;
}
@media (max-width: 900px) {
  .shell {
    grid-template-columns: 72px minmax(0, 1fr);
  }
}
</style>
