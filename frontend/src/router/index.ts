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
      path: '/model-settings',
      name: 'ModelSettings',
      component: () => import('../views/ModelSettingsView.vue'),
    },
  ],
})

export default router
