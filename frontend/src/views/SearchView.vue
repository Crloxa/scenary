<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '@/api/search'
import NoteCard from '@/components/NoteCard.vue'
import SkeletonCard from '@/components/SkeletonCard.vue'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'
import { socialApi } from '@/api/social'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const input = ref('')
const selectedSort = ref('recent')
const cards = ref([])
const nextCursor = ref(undefined)
const hasMore = ref(false)
const firstLoading = ref(false)
const loadingMore = ref(false)
const errorMessage = ref('')
let observer
let requestSeq = 0

const routeQuery = computed(() => typeof route.query.q === 'string' ? route.query.q : '')
const routeSort = computed(() => route.query.sort === 'relevance' ? 'relevance' : 'recent')
const hasQuery = computed(() => routeQuery.value.trim().length > 0)

function submitSearch() {
  const q = input.value.trim()
  const query = q ? { q, ...(selectedSort.value === 'relevance' ? { sort: 'relevance' } : {}) } : {}
  router.push({ path: '/search', query })
}

async function load(reset = false, q = routeQuery.value, sort = routeSort.value) {
  if (loadingMore.value && !reset) return
  const seq = ++requestSeq
  if (!q.trim()) {
    cards.value = []
    nextCursor.value = undefined
    hasMore.value = false
    errorMessage.value = ''
    firstLoading.value = false
    loadingMore.value = false
    return
  }
  reset ? (firstLoading.value = true) : (loadingMore.value = true)
  try {
    const page = await searchApi.notes({
      q,
      sort,
      cursor: reset ? undefined : nextCursor.value,
      limit: 10,
    })
    if (seq !== requestSeq) return
    const seen = new Set(reset ? [] : cards.value.map(card => card.id))
    const fresh = page.list.filter(card => !seen.has(card.id))
    reset ? (cards.value = fresh) : cards.value.push(...fresh)
    nextCursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore)
    errorMessage.value = ''
  } catch (error) {
    if (seq === requestSeq) {
      errorMessage.value = getErrorText(error)
      toast(errorMessage.value, 'error')
    }
  } finally {
    if (seq === requestSeq) {
      firstLoading.value = false
      loadingMore.value = false
    }
  }
}

watch([routeQuery, routeSort], ([q, sort]) => {
  input.value = q
  selectedSort.value = sort
  load(true, q, sort)
}, { immediate: true })

onMounted(() => {
  observer = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting && hasMore.value && !loadingMore.value) load()
  }, { rootMargin: '400px' })
  const el = document.getElementById('search-sentinel')
  if (el) observer.observe(el)
})
onUnmounted(() => {
  requestSeq++
  observer?.disconnect()
})

function open(noteId) { router.push(`/note/${noteId}`) }
function openUser(userId) {
  if (userId != null) router.push(`/user/${userId}`)
}
async function socialAction({ type, id, enabled }) {
  if (!store.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: `/search?q=${encodeURIComponent(routeQuery.value)}` } })
    return
  }
  const card = cards.value.find(item => item.id === id)
  if (!card || card.socialPending) return
  card.socialPending = true
  try {
    card.social = type === 'like'
      ? await socialApi.like(id, enabled)
      : await socialApi.bookmark(id, enabled)
  } catch (error) {
    toast(getErrorText(error), 'error')
  } finally {
    card.socialPending = false
  }
}
</script>

<template>
  <section class="pt-6">
    <div class="max-w-[720px] mx-auto">
      <div class="flex items-end gap-3">
        <div class="flex-1">
          <label for="search-input" class="block text-sm font-medium mb-1.5">搜索风景</label>
          <input id="search-input" v-model="input" type="search" maxlength="64" placeholder="试试“云海”或作者昵称" class="w-full h-11 px-4 rounded-xl border border-line bg-surface outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100" @keyup.enter="submitSearch" />
        </div>
        <button type="button" class="h-11 px-5 rounded-xl bg-brand-500 hover:bg-brand-600 text-white font-medium" @click="submitSearch">搜索</button>
      </div>

      <div class="mt-4 flex items-center justify-between gap-3">
        <p class="text-sm text-ink-soft" aria-live="polite">
          {{ hasQuery ? `“${routeQuery}”的搜索结果` : '输入至少 2 个字符，开始发现风景' }}
        </p>
        <label class="text-sm text-ink-soft shrink-0">
          <span class="sr-only">排序方式</span>
          <select v-model="selectedSort" class="h-9 px-2 rounded-lg border border-line bg-surface text-ink" @change="submitSearch">
            <option value="recent">最新</option>
            <option value="relevance">相关性</option>
          </select>
        </label>
      </div>
    </div>

    <div v-if="firstLoading" class="mt-5 columns-2 gap-4">
      <SkeletonCard v-for="i in 6" :key="i" />
    </div>
    <div v-else-if="errorMessage && cards.length === 0" class="py-24 text-center text-ink-soft">
      <p class="mb-4">搜索失败：{{ errorMessage }}</p>
      <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="load(true)">重试</button>
    </div>
    <div v-else-if="!hasQuery" class="py-24 text-center text-ink-soft">
      搜索标题、正文、地点或作者昵称。
    </div>
    <div v-else-if="cards.length === 0" class="py-24 text-center text-ink-soft">
      没找到相关风景，换个关键词试试。
    </div>
    <template v-else>
      <div class="mt-5 columns-2 gap-4" data-testid="search-results">
        <NoteCard
          v-for="card in cards"
          :key="card.id"
          :card="card"
          @open="open"
          @open-user="openUser"
          @social-action="socialAction"
        />
      </div>
      <div id="search-sentinel" class="h-10"></div>
      <div v-if="errorMessage" class="text-center text-sm text-red-500 py-2">
        <span>加载更多失败：{{ errorMessage }}</span>
        <button class="ml-2 underline" @click="load(false)">重试</button>
      </div>
      <p v-if="loadingMore" class="text-center text-xs text-ink-soft py-2">加载中…</p>
      <p v-else-if="!hasMore" class="text-center text-xs text-ink-soft py-4">— 到底啦 —</p>
    </template>
  </section>
</template>
