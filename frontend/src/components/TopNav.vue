<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { isDark, toggleTheme } from '@/utils/theme'
import { toast } from '@/utils/toast'
import { authApi } from '@/api/auth'
import { notificationApi } from '@/api/notification'
import { connectNotificationRealtime } from '@/utils/notificationRealtime'

const router = useRouter()
const store = useUserStore()
const menuOpen = ref(false)
const rootEl = ref(null)
const darkMode = ref(isDark())
const loggingOut = ref(false)
const avatarImageFailed = ref(false)
const unreadCount = ref(0)
let notificationTimer = null
let stopNotificationRealtime = null

watch(() => store.avatarUrl, () => { avatarImageFailed.value = false })

function onToggleTheme() {
  const next = toggleTheme()
  darkMode.value = next === 'dark'
}

function closeOnOutside(e) {
  if (rootEl.value && !rootEl.value.contains(e.target)) menuOpen.value = false
}
onMounted(() => document.addEventListener('click', closeOnOutside))
onMounted(() => {
  startNotificationPolling()
  startNotificationRealtime()
})
onUnmounted(() => {
  document.removeEventListener('click', closeOnOutside)
  clearInterval(notificationTimer)
  stopNotificationRealtime?.()
})

watch(() => [store.isLoggedIn, store.accessToken], ([loggedIn]) => {
  if (loggedIn) {
    startNotificationPolling()
    startNotificationRealtime()
  }
  else {
    clearInterval(notificationTimer)
    unreadCount.value = 0
    stopNotificationRealtime?.()
    stopNotificationRealtime = null
  }
})

async function refreshNotificationCount() {
  if (!store.isLoggedIn) return
  try {
    const page = await notificationApi.list({ limit: 1 })
    unreadCount.value = Number(page.unreadCount || 0)
  } catch {
    // 通知轮询降级，不阻塞导航和主页面
  }
}

function startNotificationPolling() {
  clearInterval(notificationTimer)
  if (!store.isLoggedIn) return
  refreshNotificationCount()
  notificationTimer = window.setInterval(refreshNotificationCount, 30000)
}

function startNotificationRealtime() {
  stopNotificationRealtime?.()
  stopNotificationRealtime = connectNotificationRealtime({
    accessToken: store.accessToken,
    onNotification: refreshNotificationCount,
  })
}

function goPublish() {
  if (!store.isLoggedIn) {
    toast('请先登录后再发布', 'error')
    return router.push({ path: '/login', query: { redirect: '/publish' } })
  }
  return router.push('/publish')
}
const searchText = ref('')
function submitSearch() {
  const q = searchText.value.trim()
  router.push({ path: '/search', query: q ? { q } : {} })
}
async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    if (store.accessToken) await authApi.logout()
  } catch {
    // 本地清态必须成功，服务端失败由下次令牌校验兜底
  } finally {
    store.forceLogout()
    menuOpen.value = false
    loggingOut.value = false
    router.push('/')
  }
}
</script>

<template>
  <header class="sticky top-0 z-40 backdrop-blur bg-paper/85 border-b border-line">
    <div class="max-w-[1100px] mx-auto px-4 max-[360px]:px-2 h-14 flex items-center gap-4">
      <RouterLink to="/" class="flex items-center gap-2 select-none shrink-0 whitespace-nowrap">
        <span class="text-xl leading-none">🏔</span>
        <span class="font-semibold tracking-wide text-brand-500">Scenary</span>
      </RouterLink>

      <form data-testid="nav-search" class="hidden md:flex flex-1 max-w-[360px]" @submit.prevent="submitSearch">
        <label class="sr-only" for="nav-search-input">搜索风景</label>
        <div class="relative w-full">
          <svg aria-hidden="true" class="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
          <input id="nav-search-input" v-model="searchText" type="search" placeholder="搜索风景、地点或作者" class="w-full h-9 pl-9 pr-3 rounded-full border border-line bg-surface text-sm outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100" />
        </div>
      </form>

      <div class="ml-auto flex items-center gap-3 max-[360px]:gap-1 shrink-0">
        <button
          data-testid="nav-theme"
          :aria-label="darkMode ? '切换到日间模式' : '切换到夜间模式'"
          class="w-9 h-9 shrink-0 rounded-full grid place-items-center text-base hover:bg-mute transition"
          @click="onToggleTheme"
        >
          {{ darkMode ? '☀️' : '🌙' }}
        </button>

        <button
          data-testid="nav-publish"
          class="inline-flex items-center gap-1.5 h-9 px-4 max-[360px]:px-3 shrink-0 whitespace-nowrap rounded-full bg-brand-500 hover:bg-brand-600 active:scale-95 transition text-white text-sm font-medium"
          @click="goPublish"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
          <span>发布</span>
        </button>

        <template v-if="store.isLoggedIn">
          <button
            type="button"
            data-testid="nav-notifications"
            :aria-label="unreadCount > 0 ? `通知，未读 ${unreadCount} 条` : '通知'"
            class="relative w-9 h-9 shrink-0 rounded-full grid place-items-center text-base hover:bg-mute transition"
            @click="router.push('/notifications')"
          >
            <span aria-hidden="true">♡</span>
            <span v-if="unreadCount > 0" aria-live="polite" class="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-red-500 text-white text-[10px] leading-4">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </button>
          <div ref="rootEl" class="relative">
            <button
              data-testid="nav-avatar"
              type="button"
              aria-label="打开用户菜单"
              :aria-expanded="menuOpen"
              aria-controls="user-menu"
              class="w-9 h-9 rounded-full overflow-hidden ring-2 ring-brand-100 hover:ring-brand-300 transition"
              @click="menuOpen = !menuOpen"
            >
              <img v-if="store.avatarUrl && !avatarImageFailed" :src="store.avatarUrl" alt="头像" class="w-full h-full object-cover" @error="avatarImageFailed = true" />
              <span v-else class="block w-full h-full grid place-items-center bg-brand-50 text-brand-400 text-sm">{{ (store.nickname || 'U').slice(0, 1) }}</span>
            </button>
            <div
              v-if="menuOpen"
              id="user-menu"
              role="menu"
              class="absolute right-0 mt-2 w-40 bg-surface rounded-xl shadow-lg border border-line py-1.5 text-sm"
            >
              <div class="px-3 py-1.5 text-xs text-ink-soft truncate">{{ store.nickname }}</div>
              <button
                role="menuitem"
                class="w-full text-left px-3 py-2 hover:bg-brand-50"
                @click="router.push(`/user/${store.userId}`); menuOpen = false"
              >
                我的主页
              </button>
              <button
                role="menuitem"
                class="w-full text-left px-3 py-2 hover:bg-brand-50"
                @click="router.push('/bookmarks'); menuOpen = false"
              >
                我的收藏
              </button>
              <button
                role="menuitem"
                class="w-full text-left px-3 py-2 hover:bg-brand-50"
                @click="router.push('/notifications'); menuOpen = false"
              >
                通知<span v-if="unreadCount" class="ml-1 text-xs text-red-500">({{ unreadCount }})</span>
              </button>
              <button role="menuitem" :disabled="loggingOut" class="w-full text-left px-3 py-2 hover:bg-brand-50 text-red-500 disabled:opacity-60" data-testid="nav-logout" @click="logout">
                {{ loggingOut ? '退出中…' : '退出登录' }}
              </button>
            </div>
          </div>
        </template>
        <RouterLink
          v-else
          to="/login"
          class="h-9 px-4 max-[360px]:px-3 shrink-0 whitespace-nowrap inline-flex items-center rounded-full border border-brand-200 text-brand-600 hover:bg-brand-50 transition text-sm"
        >
          登录 / 注册
        </RouterLink>
      </div>
    </div>
  </header>
</template>
