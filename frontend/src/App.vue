<script setup lang="ts">
// 根布局：顶部导航（对话/知识库/模型设置）+ 路由出口；高亮项跟随当前路径。
import { RouterView, useRoute } from 'vue-router'
import { ref, watch } from 'vue'

const route = useRoute()
const activeMenu = ref(route.path)

watch(
  () => route.path,
  (path) => {
    activeMenu.value = path
  },
)
</script>

<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <h1>KnowFlow</h1>
      <el-menu
        :default-active="activeMenu"
        mode="horizontal"
        router
        class="nav-menu"
        :ellipsis="false"
      >
        <el-menu-item index="/chat"> 对话 </el-menu-item>
        <el-menu-item index="/knowledge-bases"> 知识库 </el-menu-item>
        <el-menu-item index="/processing"> 处理任务 </el-menu-item>
        <el-menu-item index="/model-settings"> 模型设置 </el-menu-item>
      </el-menu>
    </el-header>
    <el-main>
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
}
.app-header {
  display: flex;
  align-items: center;
  background-color: #409eff;
  color: #fff;
  padding: 0 20px;
}
.app-header h1 {
  font-size: 1.2rem;
  font-weight: 600;
  margin-right: 32px;
  white-space: nowrap;
}
.nav-menu {
  flex: 1;
  border-bottom: none;
  background: transparent;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.15);
}
.nav-menu :deep(.el-menu-item) {
  color: #fff;
}
.nav-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.2);
}
</style>
