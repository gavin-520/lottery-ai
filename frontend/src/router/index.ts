import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue')
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/HistoryView.vue')
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('@/views/AnalyticsView.vue'),
          meta: { roles: ['ADMIN', 'ANALYST', 'USER'] }
        },
        {
          path: 'predict',
          name: 'predict',
          component: () => import('@/views/PredictView.vue'),
          meta: { roles: ['ADMIN', 'ANALYST'] }
        },
        {
          path: 'backtest',
          name: 'backtest',
          component: () => import('@/views/BacktestView.vue'),
          meta: { roles: ['ADMIN', 'ANALYST'] }
        },
        {
          path: 'agent',
          name: 'agent',
          component: () => import('@/views/AgentView.vue'),
          meta: { roles: ['ADMIN', 'ANALYST'] }
        },
        {
          path: 'workflow',
          name: 'workflow',
          component: () => import('@/views/WorkflowView.vue'),
          meta: { roles: ['ADMIN', 'ANALYST'] }
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/ModelsView.vue'),
          meta: { roles: ['ADMIN', 'ANALYST'] }
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/AdminView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: 'events',
          name: 'events',
          component: () => import('@/views/EventsView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: 'sla',
          name: 'sla',
          component: () => import('@/views/SlaView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: 'sync',
          name: 'sync',
          component: () => import('@/views/SyncView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: 'ops',
          name: 'ops',
          component: () => import('@/views/OpsView.vue'),
          meta: { roles: ['ADMIN'] }
        }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.token) {
    return { name: 'dashboard' }
  }
  const roles = to.meta.roles as string[] | undefined
  if (roles && !auth.hasRole(...roles)) {
    return { name: 'dashboard' }
  }
})

export default router
