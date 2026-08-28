<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { noteApi } from '@/api/note'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const detail = ref(null)
const notFound = ref(false)
const deleting = ref(false)
const armDelete = ref(false)
let disarmTimer = null

onMounted(async () => {
  try {
    detail.value = await noteApi.detail(route.params.id)
  } catch (e) {
    if (e?.code === 40400) notFound.value = true
    else toast(getErrorText(e), 'error')
  }
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

  <article v-else-if="detail" class="max-w-[720px] mx-auto pt-6">
    <h1 data-testid="note-title" class="text-2xl font-semibold leading-snug">{{ detail.title }}</h1>
    <div class="mt-3 flex items-center gap-3">
      <button class="flex items-center gap-2 group" @click="router.push(`/user/${detail.author.id}`)">
        <img v-if="detail.author.avatarUrl" :src="detail.author.avatarUrl" class="w-9 h-9 rounded-full object-cover" alt="" />
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
      <img
        v-for="(img, i) in detail.images"
        :key="img.mediaId"
        :src="img.url"
        :alt="`${detail.title} 图 ${i + 1}`"
        loading="lazy"
        class="w-full max-h-[720px] object-contain bg-black/95 rounded-xl"
      />
    </div>

    <p v-if="detail.content" class="mt-5 whitespace-pre-wrap text-[15px] leading-relaxed">{{ detail.content }}</p>
  </article>
</template>
