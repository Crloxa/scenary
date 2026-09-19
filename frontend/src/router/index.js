import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/publish',
      name: 'publish',
      component: () => import('@/views/PublishView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // P16-01 编辑模式：同一视图按参数区分，复用媒体/表单/校验逻辑
      path: '/publish/:noteId(\\d+)',
      name: 'publish-edit',
      component: () => import('@/views/PublishView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/bookmarks',
      name: 'bookmarks',
      component: () => import('@/views/BookmarksView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/note/:id', name: 'note', component: () => import('@/views/NoteDetailView.vue') },
    {
      path: '/user/:id(\\d+)',
      name: 'user',
      component: () => import('@/views/ProfileView.vue'),
    },
    // P16-02 关注关系列表
    {
      path: '/user/:id(\\d+)/followers',
      name: 'user-followers',
      component: () => import('@/views/FollowListView.vue'),
    },
    {
      path: '/user/:id(\\d+)/following',
      name: 'user-following',
      component: () => import('@/views/FollowListView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notfound',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach(to => {
  const store = useUserStore()
  if (to.meta.requiresAuth && !store.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && store.isLoggedIn) {
    return { path: '/' }
  }
})

export default router
