// 一级路由：/flow、/chat、/knowledge-bases、/knowledge-bases/:id、/documents/:id、/processing、/candidates、/model-settings；
// /knowledge-items/:id 为历史路径，重定向到 /documents/:id；Upload 是知识库流程入口而非一级导航。
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/flow',
    },
    {
      path: '/flow',
      name: 'Flow',
      component: () => import('../views/FlowView.vue'),
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
      path: '/knowledge-bases/:id',
      name: 'KnowledgeBaseDetail',
      component: () => import('../views/KnowledgeBaseDetailView.vue'),
    },
    {
      path: '/documents/:id',
      name: 'KnowledgeDocumentDetail',
      component: () => import('../views/KnowledgeDocumentDetailView.vue'),
    },
    {
      path: '/knowledge-items/:id',
      redirect: (to) => `/documents/${to.params.id}`,
    },
    {
      path: '/processing',
      name: 'Processing',
      component: () => import('../views/ProcessingView.vue'),
    },
    {
      path: '/candidates',
      name: 'Candidates',
      component: () => import('../views/CandidatesView.vue'),
    },
    {
      path: '/model-settings',
      name: 'ModelSettings',
      component: () => import('../views/ModelSettingsView.vue'),
    },
  ],
})

export default router
