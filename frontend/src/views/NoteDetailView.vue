<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { noteApi } from '@/api/note'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const notFound = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const brokenImages = ref(new Set())
const authorImageFailed = ref(false)
const deleting = ref(false)
const armDelete = ref(false)
let disarmTimer = null
let loadSeq = 0

async function loadDetail(targetId = route.params.id, seq = loadSeq) {
  detail.value = null
  notFound.value = false
  errorMessage.value = ''
  brokenImages.value = new Set()
  authorImageFailed.value = false
  loading.value = true
  try {
    const nextDetail = await noteApi.detail(targetId)
    if (seq !== loadSeq || targetId !== route.params.id) return
    detail.value = nextDetail
  } catch (e) {
    if (seq !== loadSeq || targetId !== route.params.id) return
    if (e?.code === 40400) notFound.value = true
    else {
      errorMessage.value = getErrorText(e)
      toast(errorMessage.value, 'error')
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function markImageFailed(mediaId) {
  brokenImages.value = new Set(brokenImages.value).add(mediaId)
}

function reloadForRoute() {
  const seq = ++loadSeq
  armDelete.value = false
  clearTimeout(disarmTimer)
  loadDetail(route.params.id, seq)
}

onMounted(reloadForRoute)
watch(() => route.params.id, reloadForRoute)
onUnmounted(() => {
  loadSeq++
  clearTimeout(disarmTimer)
})

async function removeNote() {
  // 两段式确认：第一击武装，2.5 秒内第二击执行
  if (!armDelete.value) {
    armDelete.value = true
    clearTimeout(disarmTimer)
    disarmTimer = setTimeout(() => (armDelete.value = false), 2500)
    return
  }
  clearTimeout(disarmTimer)
  deleting.value = true
  try {
    await noteApi.remove(detail.value.id)
    toast('已删除')
    router.push('/')
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    deleting.value = false
    armDelete.value = false
  }
}
function fmt(ts) {
  return new Date(Number(ts)).toLocaleString()
}
</script>

<template>
  <div v-if="notFound" class="py-28 text-center text-ink-soft">
    <p class="text-5xl mb-3">🍂</p>
    <p class="mb-6">笔记不存在，或作者已将其设为私密</p>
    <button class="h-10 px-6 rounded-full bg-brand-500 text-white" @click="router.push('/')">回首页</button>
  </div>

  <div v-else-if="loading" class="max-w-[720px] mx-auto pt-6 animate-pulse">
    <div class="h-8 w-2/3 rounded bg-mute"></div>
    <div class="mt-4 h-72 rounded-xl bg-mute"></div>
  </div>

  <div v-else-if="errorMessage" class="py-28 text-center text-ink-soft">
    <p class="mb-4">加载失败：{{ errorMessage }}</p>
    <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="reloadForRoute">重试</button>
  </div>

  <article v-else-if="detail" class="max-w-[720px] mx-auto pt-6">
    <h1 data-testid="note-title" class="text-2xl font-semibold leading-snug">{{ detail.title }}</h1>
    <div class="mt-3 flex items-center gap-3">
      <button class="flex items-center gap-2 group" @click="router.push(`/user/${detail.author.id}`)">
        <img v-if="detail.author.avatarUrl && !authorImageFailed" :src="detail.author.avatarUrl" class="w-9 h-9 rounded-full object-cover" alt="" @error="authorImageFailed = true" />
        <span v-else class="w-9 h-9 rounded-full bg-brand-50 grid place-items-center text-brand-400">{{ (detail.author.nickname||'山').slice(0,1) }}</span>
        <span class="text-sm font-medium group-hover:text-brand-600">{{ detail.author.nickname }}</span>
      </button>
      <span class="text-xs text-ink-soft">{{ fmt(detail.createdAt) }}</span>
      <span v-if="detail.placeName" class="ml-auto text-xs px-2.5 py-1 rounded-full bg-brand-50 text-brand-600">📍 {{ detail.placeName }}</span>
      <button
        v-if="detail.mine"
        data-testid="btn-del-note"
        :disabled="deleting"
        class="ml-auto text-xs h-8 px-3 rounded-full border transition"
        :class="armDelete ? 'bg-red-500 text-white border-red-500' : 'border-red-200 text-red-500 hover:bg-red-50'"
        @click="removeNote"
      >
        {{ armDelete ? '再点一次确认删除' : '删除' }}
      </button>
    </div>

    <!-- 大图纵向流 -->
    <div class="mt-4 space-y-3">
      <template v-for="(img, i) in detail.images" :key="img.mediaId">
        <img
          v-if="!brokenImages.has(img.mediaId)"
          :src="img.url"
          :alt="`${detail.title} 图 ${i + 1}`"
          loading="lazy"
          class="w-full max-h-[720px] object-contain bg-black/95 rounded-xl"
          @error="markImageFailed(img.mediaId)"
        />
        <div
          v-else
          role="img"
          :aria-label="`${detail.title} 第 ${i + 1} 张图片暂时无法显示`"
          class="w-full min-h-56 grid place-items-center bg-mute rounded-xl text-sm text-ink-soft"
        >图片暂时无法显示</div>
      </template>
    </div>

    <p v-if="detail.content" class="mt-5 whitespace-pre-wrap text-[15px] leading-relaxed">{{ detail.content }}</p>
  </article>
</template>
