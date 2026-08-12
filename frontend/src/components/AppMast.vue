<script setup lang="ts">
// 顶部 mast：品牌 + 副题 + 实时日期 + "新建学习话题" CTA。
import { onMounted, onUnmounted, ref } from 'vue'

const emit = defineEmits<{ createTopic: [] }>()

const today = ref('')

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

function formatDate(d: Date): string {
  const month = d.getMonth() + 1
  const day = d.getDate()
  const weekday = WEEKDAYS[d.getDay()]
  return `${month} 月 ${day} 日 · 星期${weekday}`
}

let timer: number | undefined

onMounted(() => {
  today.value = formatDate(new Date())
  timer = window.setInterval(() => {
    today.value = formatDate(new Date())
  }, 60_000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <header class="mast">
    <div class="brand">
      <h1>知流</h1>
      <small>个人学习与知识沉淀系统</small>
    </div>
    <div class="today">
      <span>{{ today }}</span>
      <span class="dotlive">学习中</span>
      <button class="newchat" @click="emit('createTopic')">＋ 新建学习话题</button>
    </div>
  </header>
</template>

<style scoped>
.mast {
  box-sizing: border-box;
  height: var(--kf-mast-h);
  border-bottom: 1px solid var(--kf-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px 0 32px;
  position: sticky;
  top: 0;
  background: rgba(245, 241, 232, 0.91);
  backdrop-filter: blur(14px);
  z-index: 15;
}
.brand {
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.brand h1 {
  font-weight: 900;
  font-size: 26px;
  margin: 0;
  letter-spacing: -1.5px;
}
.brand small {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--kf-muted);
  font-weight: 800;
}
.today {
  display: flex;
  align-items: center;
  gap: 18px;
  color: var(--kf-muted);
  font-size: 12px;
  font-weight: 700;
}
.dotlive {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--kf-ink);
  font-weight: 800;
}
.dotlive::before {
  content: '';
  width: 7px;
  height: 7px;
  background: var(--kf-hot);
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgba(255, 90, 54, 0.12);
}
.newchat {
  border: 1px solid var(--kf-ink);
  background: var(--kf-ink);
  color: var(--kf-paper);
  height: 36px;
  padding: 0 17px;
  border-radius: 14px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 900;
  transition: 0.2s;
}
.newchat:hover {
  transform: translateY(-2px) rotate(-1deg);
  box-shadow: 0 7px 0 var(--kf-red);
}
</style>
