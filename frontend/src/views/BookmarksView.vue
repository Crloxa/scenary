<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { socialApi } from '@/api/social'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const router = useRouter()
const items = ref([])
const nextCursor = ref(undefined)
const hasMore = ref(true)
const loading = ref(false)
const errorMessage = ref('')
let observer

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const page = await socialApi.bookmarks({ cursor: nextCursor.value, limit: 10 })
    const seen = new Set(items.value.map(item => item.id))
    items.value.push(...page.list.filter(item => !seen.has(item.id)))
    nextCursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore)
    errorMessage.value = ''
  } catch (e) {
    errorMessage.value = getErrorText(e)
    toast(errorMessage.value, 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadMore()
  await nextTick()
  observer = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting) loadMore()
  }, { rootMargin: '300px' })
  const el = document.getElementById('bookmark-sentinel')
  if (el) observer.observe(el)
})
onUnmounted(() => observer?.disconnect())
</script>

<template>
  <section class="max-w-[720px] mx-auto pt-6">
    <div class="flex items-baseline justify-between">
      <div>
        <h1 class="text-2xl font-semibold">我的收藏</h1>
        <p class="mt-1 text-sm text-ink-soft">把想再看一次的风景留在这里</p>
      </div>
      <button class="text-sm text-ink-soft hover:text-brand-600" @click="router.push('/')">回首页</button>
    </div>

    <div v-if="errorMessage && items.length === 0" class="py-24 text-center text-ink-soft">
      <p class="mb-4">收藏加载失败：{{ errorMessage }}</p>
      <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="loadMore">重试</button>
    </div>
    <div v-else-if="items.length === 0 && !loading" class="py-24 text-center text-ink-soft">
      还没有收藏，去首页发现风景吧。
    </div>
    <div v-else class="mt-5 grid gap-3">
      <button
        v-for="item in items"
        :key="item.id"
        class="text-left flex gap-3 p-3 rounded-xl border border-line bg-surface hover:bg-mute transition"
        @click="router.push(`/note/${item.id}`)"
      >
        <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title || '收藏笔记'" class="w-24 h-24 rounded-lg object-cover bg-mute" />
        <div class="min-w-0 py-1">
          <h2 class="font-medium truncate">{{ item.title }}</h2>
          <p class="mt-1 text-sm text-ink-soft line-clamp-2">{{ item.contentPreview }}</p>
          <p class="mt-2 text-xs text-ink-soft">{{ item.author?.nickname }} · 已收藏</p>
        </div>
      </button>
    </div>
    <div id="bookmark-sentinel" class="h-10"></div>
    <p v-if="loading" class="text-center text-xs text-ink-soft py-2">加载中…</p>
    <div v-if="errorMessage && items.length > 0" class="text-center text-sm text-red-500 py-2">
      <span>加载更多失败：{{ errorMessage }}</span>
      <button class="ml-2 underline" @click="loadMore">重试</button>
    </div>
    <p v-else-if="!hasMore && items.length > 0" class="text-center text-xs text-ink-soft py-4">— 到底啦 —</p>
  </section>
</template>
