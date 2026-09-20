import { createRouter, createWebHashHistory } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

const router = createRouter({
  // hash 模式：打包进 jar 后 /settings 直接刷新不会 404，后端无需加 forward
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'library',
      component: () => import('@/views/LibraryView.vue'),
      meta: { title: '漫画库' },
    },
    // 阅读器不再走路由：由 App.vue 里的 tab 页承载（见 stores/tabs.ts）
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
      meta: { title: '设置' },
    },
  ],
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 漫画阅读器` : '漫画阅读器'
})

export default router
