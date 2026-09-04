<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi } from '@/api/notification'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const router = useRouter()
const items = ref([])
const nextCursor = ref(undefined)
const hasMore = ref(true)
const unreadCount = ref(0)
const loading = ref(false)
const errorMessage = ref('')
let observer

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const page = await notificationApi.list({ cursor: nextCursor.value, limit: 10 })
    const seen = new Set(items.value.map(item => item.id))
    items.value.push(...page.list.filter(item => !seen.has(item.id)))
    nextCursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore)
    unreadCount.value = Number(page.unreadCount || 0)
    errorMessage.value = ''
  } catch (e) {
    errorMessage.value = getErrorText(e)
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  if (!unreadCount.value) return
  try {
    await notificationApi.markRead([])
    items.value = items.value.map(item => item.readAt ? item : { ...item, readAt: Date.now() })
    unreadCount.value = 0
  } catch (e) {
    toast(getErrorText(e), 'error')
  }
}

async function openItem(item) {
  if (!item.readAt) {
    try {
      await notificationApi.markRead([item.id])
      item.readAt = Date.now()
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // 打开关联内容优先，已读失败可由“全部已读”重试
    }
  }
  if (item.noteId) router.push(`/note/${item.noteId}`)
}

function typeText(item) {
  return {
    LIKE: '赞了你的笔记',
    FOLLOW: '关注了你',
    COMMENT: '评论了你的笔记',
    REPLY: '回复了你的评论',
  }[item.type] || '与你产生了互动'
}

function fmt(ts) {
  return new Date(Number(ts)).toLocaleString()
}

onMounted(async () => {
  await loadMore()
  await nextTick()
  observer = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting) loadMore()
  }, { rootMargin: '300px' })
  const el = document.getElementById('notification-sentinel')
  if (el) observer.observe(el)
})
onUnmounted(() => observer?.disconnect())
</script>

<template>
  <section class="max-w-[720px] mx-auto pt-6" aria-labelledby="notifications-title">
    <div class="flex items-baseline justify-between">
      <div>
        <h1 id="notifications-title" class="text-2xl font-semibold">通知</h1>
        <p class="mt-1 text-sm text-ink-soft">评论、点赞和关注都会留在这里</p>
      </div>
      <button type="button" class="text-sm text-brand-600 disabled:text-ink-soft" :disabled="!unreadCount" @click="markAllRead">全部已读<span v-if="unreadCount">（{{ unreadCount }}）</span></button>
    </div>

    <div v-if="errorMessage && items.length === 0" role="alert" class="py-24 text-center text-ink-soft">
      <p class="mb-4">通知加载失败：{{ errorMessage }}</p>
      <button type="button" class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="loadMore">重试</button>
    </div>
    <div v-else-if="items.length === 0 && !loading" class="py-24 text-center text-ink-soft">
      还没有新通知。
    </div>
    <div v-else class="mt-5 divide-y divide-line rounded-2xl border border-line bg-surface">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        data-testid="notification-item"
        class="w-full text-left flex gap-3 p-4 hover:bg-mute transition"
        :class="item.readAt ? '' : 'bg-brand-50/50 dark:bg-brand-900/10'"
        @click="openItem(item)"
      >
        <span class="w-9 h-9 shrink-0 rounded-full bg-brand-50 grid place-items-center text-brand-500">{{ (item.actor?.nickname || '山').slice(0, 1) }}</span>
        <span class="min-w-0 flex-1">
          <span class="block text-sm"><strong>{{ item.actor?.nickname || '已注销' }}</strong> {{ typeText(item) }}<i v-if="!item.readAt" class="inline-block ml-2 w-1.5 h-1.5 rounded-full bg-red-500 align-middle"></i></span>
          <span v-if="item.noteTitle" class="block mt-1 text-xs text-ink-soft truncate">《{{ item.noteTitle }}》</span>
          <span v-if="item.commentPreview" class="block mt-1 text-sm text-ink-soft line-clamp-2">{{ item.commentPreview }}</span>
          <span class="block mt-1 text-xs text-ink-soft">{{ fmt(item.createdAt) }}</span>
        </span>
      </button>
    </div>
    <div id="notification-sentinel" class="h-10"></div>
    <p v-if="loading" role="status" aria-live="polite" class="text-center text-xs text-ink-soft py-2">加载中…</p>
    <div v-if="errorMessage && items.length > 0" role="alert" class="text-center text-sm text-red-500 py-2">
      <span>加载更多失败：{{ errorMessage }}</span>
      <button type="button" class="ml-2 underline" @click="loadMore">重试</button>
    </div>
    <p v-else-if="!hasMore && items.length > 0" class="text-center text-xs text-ink-soft py-4">— 到底啦 —</p>
  </section>
</template>
