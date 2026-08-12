<script setup lang="ts">
// 左侧主 rail：logo + 图标中文导航 + 底部用户位。导航入口全部映射真实路由，无假链接。
import { useRoute } from 'vue-router'

interface RailItem {
  to: string
  label: string
  // 内联 SVG path（24x24 描边风格，来自设计稿）
  icon: string
}

const workItems: RailItem[] = [
  {
    to: '/flow',
    label: '学习流',
    icon: 'M4 7h10M4 12h16M4 17h8M17 4l3 3-3 3',
  },
  {
    to: '/chat',
    label: 'AI 对话',
    icon: 'M5 5h14v10H9l-4 4V5ZM9 9h6M9 12h4',
  },
  {
    to: '/knowledge-bases',
    label: '知识库',
    icon: 'M5 5.5A2.5 2.5 0 0 1 7.5 3H20v16H7.5A2.5 2.5 0 0 0 5 21.5zM9 7h7M9 11h5',
  },
  {
    to: '/candidates',
    label: '待沉淀',
    icon: 'M12 3l2.2 5.3L20 9l-4.3 3.8L17 18l-5-2.8L7 18l1.3-5.2L4 9l5.8-.7z',
  },
  {
    to: '/processing',
    label: '处理任务',
    icon: 'M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M9 21H5a2 2 0 0 1-2-2v-4M15 21h4a2 2 0 0 0 2-2v-4',
  },
]

const settingsItem: RailItem = {
  to: '/model-settings',
  label: '系统设置',
  icon: 'M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1A7 7 0 0 0 15 6l-.3-2.6h-4L10.5 6A7 7 0 0 0 9 7.1l-2.4-1-2 3.4L6.5 11a7 7 0 0 0 0 2L4.6 14.5l2 3.4 2.4-1A7 7 0 0 0 10.5 18l.3 2.6h4L15 18a7 7 0 0 0 1.5-1.1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1z',
}

const route = useRoute()

/** 激活态：仅当前路由路径以入口前缀开头时高亮。 */
function isActive(item: RailItem): boolean {
  return route.path === item.to || route.path.startsWith(`${item.to}/`)
}
</script>

<template>
  <aside class="rail" aria-label="主导航">
    <div class="mark">知流</div>
    <nav class="railnav">
      <RouterLink
        v-for="item in workItems"
        :key="item.label"
        :to="item.to"
        class="railbtn"
        :class="{ active: isActive(item) }"
        :aria-label="item.label"
        :title="item.label"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <path :d="item.icon" />
        </svg>
        <span class="navtext">{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="railspacer" />
    <RouterLink
      :to="settingsItem.to"
      class="railbtn settingsbtn"
      :class="{ active: isActive(settingsItem) }"
      :aria-label="settingsItem.label"
      :title="settingsItem.label"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
        <path :d="settingsItem.icon" />
      </svg>
      <span class="navtext">{{ settingsItem.label }}</span>
    </RouterLink>
    <div class="footerlabel">我的空间</div>
    <div class="avatar">三金</div>
  </aside>
</template>

<style scoped>
.rail {
  box-sizing: border-box;
  position: sticky;
  top: 0;
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  border-right: 1px solid var(--kf-ink);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 22px 16px 18px;
  background: var(--kf-paper-3);
  flex: 0 0 var(--kf-rail-w);
}
.mark {
  width: 100%;
  height: 62px;
  border-radius: 18px;
  background: var(--kf-ink);
  color: var(--kf-paper);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  font-weight: 900;
  letter-spacing: -1px;
  box-shadow: 5px 5px 0 var(--kf-red);
  transform: rotate(-1deg);
}
.mark::after {
  content: '↗';
  font-size: 14px;
  margin-left: 8px;
  color: var(--kf-hot-soft);
}
.railnav {
  box-sizing: border-box;
  width: calc(100% + 19px);
  margin-top: 37px;
  margin-left: -4px;
  padding: 3px 0 6px 4px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
  min-height: 0;
}
.railnav:hover,
.railnav:focus-within {
  scrollbar-color: var(--kf-line) transparent;
}
.railnav::-webkit-scrollbar {
  width: 6px;
}
.railnav::-webkit-scrollbar-thumb {
  border-radius: var(--kf-radius-pill);
  background: transparent;
}
.railnav:hover::-webkit-scrollbar-thumb,
.railnav:focus-within::-webkit-scrollbar-thumb {
  background: var(--kf-line);
}
.railbtn {
  box-sizing: border-box;
  width: 100%;
  height: 48px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  position: relative;
  transition: 0.2s ease;
  font-size: 14px;
  font-weight: 800;
  text-align: left;
  color: inherit;
  text-decoration: none;
}
.railnav .railbtn {
  flex: 0 0 48px;
  width: calc(var(--kf-rail-w) - 32px);
}
.railbtn svg {
  width: 18px;
  height: 18px;
  stroke-width: 2;
  flex: 0 0 auto;
}
.railbtn .navtext {
  white-space: nowrap;
}
.railbtn:hover,
.railbtn.active {
  background: var(--kf-white);
  border-color: var(--kf-ink);
  box-shadow: -3px 3px 0 var(--kf-red);
}
.railbtn:focus {
  outline: 2px solid var(--kf-blue);
  outline-offset: 2px;
}
.railbtn.active::after {
  content: '';
  width: 7px;
  height: 7px;
  background: var(--kf-hot);
  border-radius: 50%;
  position: absolute;
  right: 10px;
  top: 9px;
}
.railspacer {
  flex: 1;
}
.settingsbtn {
  margin-bottom: 14px;
}
.footerlabel {
  font-size: 9px;
  letter-spacing: 0.12em;
  color: var(--kf-muted);
  padding: 0 8px 8px;
  font-weight: 900;
}
.avatar {
  height: 44px;
  border-radius: 14px;
  background: var(--kf-red);
  color: var(--kf-white);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  border: 1px solid var(--kf-ink);
  font-size: 12px;
  margin-top: 10px;
}

/* ---- 响应式收窄：窄屏隐藏文字，只留图标 + tooltip ---- */
@media (max-width: 900px) {
  .rail {
    flex: 0 0 72px;
    width: 72px;
    min-width: 0;
    padding: 22px 10px 18px;
    overflow: hidden;
  }
  .railbtn {
    justify-content: center;
    padding: 0;
    gap: 0;
  }
  .railnav .railbtn {
    width: 52px;
  }
  .railbtn .navtext,
  .footerlabel {
    display: none;
  }
  .mark {
    height: 52px;
    font-size: 17px;
  }
}
</style>
