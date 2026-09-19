<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { socialApi } from '@/api/social'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

// P16-02 关注者/正在关注列表（docs/02 §3.9/§3.10）：同一视图按路由 meta 方向复用
const route = useRoute()
const direction = computed(() => (route.name === 'user-following' ? 'following' : 'followers'))
const userId = computed(() => Number(route.params.id))

const users = ref([])
const nextCursor = ref(undefined)
const hasMore = ref(true)
const loading = ref(false)
const error = ref('')
const failed = ref(new Set())
let seq = 0

async function load(reset = false) {
  if (loading.value || (!reset && !hasMore.value)) return
  loading.value = true
  error.value = ''
  const current = ++seq
  try {
    const fetcher = direction.value === 'following' ? socialApi.following : socialApi.followers
    const page = await fetcher(userId.value, { cursor: nextCursor.value, limit: 20 })
    if (current !== seq) return
    users.value = reset ? page.list : [...users.value, ...page.list]
    nextCursor.value = page.nextCursor ?? undefined
    hasMore.value = Boolean(page.hasMore)
  } catch (e) {
    if (current !== seq) return
    if (reset) {
      error.value = getErrorText(e) || '列表加载失败'
    } else {
      toast(getErrorText(e) || '加载更多失败', 'error')
      hasMore.value = false
    }
  } finally {
    if (current === seq) loading.value = false
  }
}

function onBroken(id) {
  failed.value = new Set([...failed.value, id])
}

function avatarLetter(item) {
  return (item.nickname || '山').slice(0, 1)
}

onMounted(() => load(true))
</script>

<template>
  <section class="max-w-[560px] mx-auto pt-6">
    <h1 class="text-lg font-semibold mb-4">
      {{ direction === 'following' ? '正在关注' : '关注者' }}
    </h1>

    <p v-if="error" class="bg-surface border border-line rounded-2xl p-6 text-sm text-ink-soft" role="alert">
      {{ error }}
      <button class="ml-2 text-brand-500" data-testid="follow-list-retry" @click="load(true)">重试</button>
    </p>

    <ul v-else class="bg-surface border border-line rounded-2xl divide-y divide-line" data-testid="follow-list">
      <li v-for="u in users" :key="u.id" class="flex items-center gap-3 p-3">
        <router-link :to="`/user/${u.id}`" class="flex items-center gap-3 min-w-0 flex-1">
          <img
            v-if="u.avatarUrl && !failed.has(u.id)"
            :src="u.avatarUrl"
            alt=""
            class="w-10 h-10 rounded-full object-cover bg-brand-50 shrink-0"
            @error="onBroken(u.id)"
          />
          <span v-else class="w-10 h-10 rounded-full bg-brand-50 grid place-items-center text-brand-400 shrink-0">{{ avatarLetter(u) }}</span>
          <span class="text-sm truncate">{{ u.nickname }}</span>
        </router-link>
        <span v-if="u.following" class="text-xs text-ink-soft shrink-0">已关注</span>
      </li>
      <li v-if="!loading && !users.length" class="p-8 text-center text-sm text-ink-soft">
        {{ direction === 'following' ? '还没有关注的人' : '还没有关注者' }}
      </li>
    </ul>

    <p v-if="loading" class="py-4 text-center text-xs text-ink-soft" data-testid="follow-list-loading">加载中…</p>
    <p v-else-if="hasMore && users.length" class="py-4 text-center">
      <button class="text-xs text-ink-soft hover:text-brand-500" @click="load()">加载更多</button>
    </p>
  </section>
</template>
