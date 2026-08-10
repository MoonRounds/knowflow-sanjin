// 一级路由：/chat、/knowledge-bases、/knowledge-items/:id、/processing、/model-settings；Upload 是知识库流程入口而非一级导航。
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('../views/ChatView.vue'),
    },
    {
      path: '/knowledge-bases',
      name: 'KnowledgeBases',
      component: () => import('../views/KnowledgeBasesView.vue'),
    },
    {
      path: '/knowledge-items/:id',
      name: 'KnowledgeItemDetail',
      component: () => import('../views/KnowledgeItemDetailView.vue'),
    },
    {
      path: '/processing',
      name: 'Processing',
      component: () => import('../views/ProcessingView.vue'),
    },
    {
      path: '/model-settings',
      name: 'ModelSettings',
      component: () => import('../views/ModelSettingsView.vue'),
    },
  ],
})

export default router
