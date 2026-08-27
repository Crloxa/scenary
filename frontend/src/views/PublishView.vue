<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { mediaApi, waitProcessed } from '@/api/media'
import { noteApi } from '@/api/note'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const router = useRouter()

const MAX_FILES = 9
const ACCEPT = 'image/jpeg,image/png,image/webp,image/gif'
let uid = 0

/** items: {key,file,url,progress:'uploading'|'processing'|'ready'|'fail', mediaId?} */
const items = ref([])
const title = ref('')
const content = ref('')
const placeName = ref('')
const visibility = ref('1')
const submitting = ref(false)
const fileInput = ref(null)

const canSubmit = computed(
  () => title.value.trim().length > 0 &&
        items.value.some(i => i.progress === 'ready') &&
        !submitting.value,
)
const titleCount = computed(() => title.value.length)

function pick() {
  fileInput.value?.click()
}

function onPick(e) {
  const list = [...e.target.files]
  e.target.value = ''
  for (const f of list) {
    if (items.value.length >= MAX_FILES) {
      toast(`最多 ${MAX_FILES} 张图片`, 'error')
      break
    }
    addItem(f)
  }
}

function dragOver(e) { e.preventDefault() }
function onDrop(e) {
  e.preventDefault()
  for (const f of e.dataTransfer.files) {
    if (items.value.length >= MAX_FILES) return toast(`最多 ${MAX_FILES} 张图片`, 'error')
    if (f.type.startsWith('image/')) addItem(f)
  }
}

async function addItem(file) {
  const key = `f${++uid}`
  const item = reactive({
    key,
    file,
    url: URL.createObjectURL(file),
    progress: 'uploading',
    mediaId: null,
  })
  items.value.push(item)

  try {
    // 立即上传拿 mediaId，再轮询消费者产出缩略图
    item.mediaId = (await mediaApi.uploadOne(item.file)).mediaId
    item.progress = 'processing'
    await waitProcessed(item.mediaId)
    item.progress = 'ready'
  } catch (err) {
    item.progress = 'fail'
    toast(getErrorText(err) || '这张图处理失败，可移除后重传', 'error')
  }
}

function removeItem(key) {
  items.value = items.value.filter(i => i.key !== key)
}
function moveItem(key, dir) {
  const arr = items.value
  const i = arr.findIndex(x => x.key === key)
  const j = i + dir
  if (j < 0 || j >= arr.length) return
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
}
async function retryItem(key) {
  const it = items.value.find(i => i.key === key)
  if (!it || it.progress === 'fail' && !it.file) return
  it.progress = 'uploading'
  try {
    it.mediaId = (await mediaApi.uploadOne(it.file)).mediaId
    it.progress = 'processing'
    await waitProcessed(it.mediaId)
    it.progress = 'ready'
  } catch (err) {
    it.progress = 'fail'
    toast(getErrorText(err), 'error')
  }
}

async function submitNote() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const ordered = items.value.filter(i => i.progress === 'ready').map(i => i.mediaId)
    await noteApi.create({
      title: title.value.trim(),
      content: content.value.trim(),
      placeName: placeName.value.trim(),
      mediaIds: ordered,
      visibility: Number(visibility.value),
    })
    toast('发布成功')
    router.push('/')
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="max-w-[640px] mx-auto pt-6">
    <h1 class="text-lg font-semibold mb-4">发布笔记</h1>

    <!-- 图片选区：拖拽/点选，即时本地预览、可删可排序 -->
    <div
      class="rounded-2xl border-2 border-dashed border-line hover:border-brand-300 transition p-3 bg-surface"
      @dragover="dragOver"
      @drop="onDrop"
    >
      <div class="flex flex-wrap gap-2.5">
        <div v-for="(it, idx) in items" :key="it.key" data-testid="upload-item" class="relative w-[104px]">
          <img :src="it.url" alt="" class="w-[104px] h-[104px] object-cover rounded-xl" />
          <!-- 状态徽标 -->
          <span
            v-if="it.progress !== 'ready'"
            data-testid="badge"
            class="absolute inset-x-1 bottom-1 h-6 rounded-full text-[11px] text-white grid place-items-center backdrop-blur px-1 truncate"
            :class="it.progress === 'fail' ? 'bg-red-500/90' : 'bg-black/50'"
          >
            <template v-if="it.progress === 'uploading'">上传中…</template>
            <template v-else-if="it.progress === 'processing'">
              <svg class="animate-spin -ml-0.5 mr-1 h-3 w-3" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity=".25"/><path d="M22 12a10 10 0 0 1-10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/></svg>
              处理中
            </template>
            <template v-else>失败 · 点重传</template>
          </span>

          <!-- 操作角标 -->
          <div class="absolute top-1 right-1 flex gap-1">
            <button class="w-5 h-5 rounded-full bg-black/55 text-white text-xs leading-none grid place-items-center" title="移除" @click="removeItem(it.key)">×</button>
          </div>
          <div class="absolute top-1 left-1 flex gap-0.5">
            <button v-if="idx > 0" class="w-5 h-5 rounded-full bg-black/45 text-white text-[10px]" title="前移" @click="moveItem(it.key, -1)">←</button>
            <button v-if="idx < items.length - 1" class="w-5 h-5 rounded-full bg-black/45 text-white text-[10px]" title="后移" @click="moveItem(it.key, 1)">→</button>
          </div>
          <button
            v-if="it.progress === 'fail'"
            class="mt-1 w-full h-6 rounded-lg border border-red-200 text-red-500 text-[11px]"
            @click="retryItem(it.key)"
          >
            重传
          </button>
        </div>

        <button
          v-if="items.length < MAX_FILES"
          data-testid="pick-image"
          class="w-[104px] h-[104px] rounded-xl border border-line grid place-items-center text-ink-soft hover:text-brand-400 hover:border-brand-200 transition text-3xl"
          @click="pick"
        >
          +
        </button>
      </div>
      <p class="mt-2 text-[11px] text-ink-soft">{{ items.length }}/{{ MAX_FILES }} 张 · 支持 jpg/png/webp/gif · 可拖拽进来</p>
      <input ref="fileInput" type="file" multiple :accept="ACCEPT" class="hidden" @change="onPick" />
    </div>

    <!-- 表单 -->
    <form class="mt-4 space-y-3 bg-surface rounded-2xl border border-line p-4" @submit.prevent="submitNote">
      <div>
        <input
          v-model="title"
          data-testid="input-title"
          maxlength="64"
          placeholder="填写标题（必填）"
          class="w-full h-11 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm"
        />
        <p class="text-right text-[11px] text-ink-soft mt-0.5">{{ titleCount }}/64</p>
      </div>
      <textarea
        v-model="content"
        rows="4"
        maxlength="2000"
        placeholder="这一刻的风景与心情（选填）"
        class="w-full p-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm resize-none"
      ></textarea>
      <input
        v-model="placeName"
        maxlength="128"
        placeholder="📍 添加地点（选填）"
        class="w-full h-10 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm"
      />
      <div class="flex items-center gap-4 text-sm text-ink-soft select-none">
        <label class="inline-flex items-center gap-1.5 cursor-pointer">
          <input v-model="visibility" type="radio" value="1" class="accent-brand-500 w-4 h-4" />
          🌍 公开
        </label>
        <label class="inline-flex items-center gap-1.5 cursor-pointer">
          <input v-model="visibility" type="radio" value="0" class="accent-brand-500 w-4 h-4" />
          🔒 仅自己可见
        </label>
      </div>
      <button
        data-testid="btn-publish-submit"
        type="submit"
        :disabled="!canSubmit"
        class="w-full h-11 rounded-xl bg-brand-500 hover:bg-brand-600 disabled:bg-neutral-200 disabled:text-neutral-400 transition text-white font-medium"
      >
        {{ submitting ? '发布中…' : '发布' }}
      </button>
    </form>
  </section>
</template>
