<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { feedApi } from '@/api/feed'
import { getErrorText } from '@/utils/request'
import NoteCard from '@/components/NoteCard.vue'
import SkeletonCard from '@/components/SkeletonCard.vue'

const router = useRouter()
const cards = ref([])
const firstLoading = ref(true)
const loadingMore = ref(false)
const nextCursor = ref(undefined)
const hasMore = ref(false)
const errorMessage = ref('')
let observer

async function load(reset = false) {
  if (loadingMore.value) return
  loadingMore.value = true
  try {
    const page = await feedApi.list(reset ? {} : { cursor: nextCursor.value })
    const seen = new Set(cards.value.map(c => c.id))
    const fresh = page.list.filter(c => !seen.has(c.id))
    reset ? (cards.value = fresh) : cards.value.push(...fresh)
    nextCursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore)
    errorMessage.value = ''
  } catch (e) {
    errorMessage.value = getErrorText(e)
    window.dispatchEvent(new CustomEvent('app-toast', { detail: { msg: getErrorText(e), type: 'error' } }))
  } finally {
    firstLoading.value = false
    loadingMore.value = false
  }
}

onMounted(async () => {
  await load(true)
  // 无限滚动哨兵（docs/03 4.4）
  observer = new IntersectionObserver(
    entries => {
      if (entries[0].isIntersecting && hasMore.value && !loadingMore.value) load()
    },
    { rootMargin: '400px' },
  )
  const el = document.getElementById('feed-sentinel')
  if (el) observer.observe(el)
})
onUnmounted(() => observer?.disconnect())

watch(() => router.currentRoute.value.name === 'home', v => {
  // 从发布页返回首页时整体刷新（服务端 L1 已被写穿透失效，直接重拉最稳）
  if (v) {
    firstLoading.value = true
    load(true)
  }
})

function open(noteId) {
  router.push(`/note/${noteId}`)
}
function openUser(userId) {
  if (userId != null) router.push(`/user/${userId}`)
}
</script>

<template>
  <section class="pt-5">
    <!-- 首屏骨架屏 8 卡 -->
    <div v-if="firstLoading" class="columns-2 gap-4">
      <SkeletonCard v-for="i in 8" :key="i" />
    </div>

    <div v-else-if="errorMessage && cards.length === 0" class="py-24 text-center text-ink-soft">
      <p class="mb-4">加载失败：{{ errorMessage }}</p>
      <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="load(true)">重试</button>
    </div>

    <div v-else-if="cards.length === 0" class="py-24 text-center text-ink-soft">
      还没有风景，来发第一篇吧 🏔
    </div>

    <template v-else>
      <div class="columns-2 gap-4" data-testid="waterfall">
        <NoteCard
          v-for="c in cards"
          :key="c.id"
          :card="c"
          @open="open"
          @open-user="openUser"
        />
      </div>

      <div id="feed-sentinel" class="h-10"></div>
      <div v-if="errorMessage" class="text-center text-sm text-red-500 py-2">
        <span>加载更多失败：{{ errorMessage }}</span>
        <button class="ml-2 underline" @click="load(false)">重试</button>
      </div>
      <p v-if="loadingMore" class="text-center text-xs text-ink-soft py-2">加载中…</p>
      <p v-else-if="!hasMore && cards.length > 0" class="text-center text-xs text-ink-soft py-4">
          — 到底啦 —
        </p>
    </template>
  </section>
</template>
