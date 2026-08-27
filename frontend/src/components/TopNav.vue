<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'

const router = useRouter()
const store = useUserStore()
const menuOpen = ref(false)
const rootEl = ref(null)

function closeOnOutside(e) {
  if (rootEl.value && !rootEl.value.contains(e.target)) menuOpen.value = false
}
onMounted(() => document.addEventListener('click', closeOnOutside))
onUnmounted(() => document.removeEventListener('click', closeOnOutside))

function goPublish() {
  if (!store.isLoggedIn) {
    toast('请先登录后再发布', 'error')
    return router.push({ path: '/login', query: { redirect: '/publish' } })
  }
  return router.push('/publish')
}
function logout() {
  store.forceLogout()
  menuOpen.value = false
  router.push('/')
}
</script>

<template>
  <header class="sticky top-0 z-40 backdrop-blur bg-paper/85 border-b border-neutral-200/70">
    <div class="max-w-[1100px] mx-auto px-4 h-14 flex items-center justify-between">
      <RouterLink to="/" class="flex items-center gap-2 select-none">
        <span class="text-xl leading-none">🏔</span>
        <span class="font-semibold tracking-wide text-brand-500">Scenary</span>
      </RouterLink>

      <div class="flex items-center gap-3">
        <button
          data-testid="nav-publish"
          class="inline-flex items-center gap-1.5 h-9 px-4 rounded-full bg-brand-500 hover:bg-brand-600 active:scale-95 transition text-white text-sm font-medium"
          @click="goPublish"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
          <span>发布</span>
        </button>

        <template v-if="store.isLoggedIn">
          <div ref="rootEl" class="relative">
            <button
              data-testid="nav-avatar"
              class="w-9 h-9 rounded-full overflow-hidden ring-2 ring-brand-100 hover:ring-brand-300 transition"
              @click="menuOpen = !menuOpen"
            >
              <img v-if="store.avatarUrl" :src="store.avatarUrl" alt="avatar" class="w-full h-full object-cover" />
              <span v-else class="block w-full h-full grid place-items-center bg-brand-50 text-brand-400 text-sm">{{ (store.nickname || 'U').slice(0, 1) }}</span>
            </button>
            <div
              v-if="menuOpen"
              class="absolute right-0 mt-2 w-40 bg-white rounded-xl shadow-lg border border-neutral-100 py-1.5 text-sm"
            >
              <div class="px-3 py-1.5 text-xs text-ink-soft truncate">{{ store.nickname }}</div>
              <button
                class="w-full text-left px-3 py-2 hover:bg-brand-50"
                @click="router.push(`/user/${store.userId}`); menuOpen = false"
              >
                我的主页
              </button>
              <button class="w-full text-left px-3 py-2 hover:bg-brand-50 text-red-500" data-testid="nav-logout" @click="logout">
                退出登录
              </button>
            </div>
          </div>
        </template>
        <RouterLink
          v-else
          to="/login"
          class="h-9 px-4 inline-flex items-center rounded-full border border-brand-200 text-brand-600 hover:bg-brand-50 transition text-sm"
        >
          登录 / 注册
        </RouterLink>
      </div>
    </div>
  </header>
</template>
