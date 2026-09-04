<script setup>
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { mediaApi, waitProcessed, isAbortError } from '@/api/media'
import { noteApi } from '@/api/note'
import { useUserStore } from '@/stores/user'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const router = useRouter()
const userStore = useUserStore()

const MAX_FILES = 9
const MAX_BYTES = 10 * 1024 * 1024
const MAX_VIDEO_BYTES = 200 * 1024 * 1024
const MAX_CONCURRENT = 3
const ACCEPT = 'image/jpeg,image/png,image/gif,video/mp4,video/quicktime,video/webm'
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif']
const VIDEO_TYPES = ['video/mp4', 'video/quicktime', 'video/webm']
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif']
const VIDEO_EXTENSIONS = ['mp4', 'mov', 'webm']
let uid = 0
let activeUploads = 0
let disposed = false
let published = false
let requestKey = crypto.randomUUID()

/** items: {key,file,url,progress:'uploading'|'processing'|'ready'|'fail', mediaId?, uploadedBytes?} */
const items = ref([])
const title = ref('')
const content = ref('')
const placeName = ref('')
const latitude = ref('')
const longitude = ref('')
const placeSource = ref('MAP')
const placePrecision = ref('EXACT')
const visibility = ref('1')
const submitting = ref(false)
const fileInput = ref(null)

const coordinateError = computed(() => {
  const hasLatitude = coordinateText(latitude.value) !== ''
  const hasLongitude = coordinateText(longitude.value) !== ''
  if (!hasLatitude && !hasLongitude) return ''
  if (hasLatitude !== hasLongitude) return '纬度和经度必须同时填写'
  const lat = Number(latitude.value)
  const lng = Number(longitude.value)
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) return '纬度范围为 -90~90'
  if (!Number.isFinite(lng) || lng < -180 || lng > 180) return '经度范围为 -180~180'
  return ''
})
const hasCoordinates = computed(() => coordinateText(latitude.value) !== ''
  && coordinateText(longitude.value) !== '')
const canSubmit = computed(
  () => title.value.trim().length > 0 &&
        items.value.length > 0 &&
        items.value.every(i => i.progress === 'ready') &&
        !coordinateError.value &&
        !submitting.value,
)
const titleCount = computed(() => title.value.length)
const hasDraft = computed(() => Boolean(
  title.value.trim() || content.value.trim() || placeName.value.trim()
    || coordinateText(latitude.value) || coordinateText(longitude.value) || items.value.length,
))

function handleBeforeUnload(event) {
  if (!published && hasDraft.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(() => {
  if (published || !hasDraft.value || !userStore.isLoggedIn) return true
  if (submitting.value) return false
  return window.confirm('当前发布内容尚未完成，确定要离开吗？')
})

onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))

function pick() {
  fileInput.value?.click()
}

function onPick(e) {
  const list = [...e.target.files]
  e.target.value = ''
  for (const f of list) {
    const reason = validateFile(f)
    if (reason) {
      toast(reason, 'error')
      continue
    }
    if (isVideoFile(f) && items.value.length > 0) {
      toast('视频不能与图片或其他视频混合发布', 'error')
      continue
    }
    if (!isVideoFile(f) && items.value.some(item => item.kind === 'video')) {
      toast('视频不能与图片混合发布', 'error')
      continue
    }
    if (items.value.length >= MAX_FILES) {
      toast(`最多 ${MAX_FILES} 个媒体`, 'error')
      break
    }
    addItem(f)
  }
}

function dragOver(e) { e.preventDefault() }
function onDrop(e) {
  e.preventDefault()
  for (const f of e.dataTransfer.files) {
    if (items.value.length >= MAX_FILES) return toast(`最多 ${MAX_FILES} 个媒体`, 'error')
    const reason = validateFile(f)
    if (reason) {
      toast(reason, 'error')
      continue
    }
    if ((isVideoFile(f) && items.value.length > 0)
      || (!isVideoFile(f) && items.value.some(item => item.kind === 'video'))) {
      toast('视频不能与图片混合发布', 'error')
      continue
    }
    addItem(f)
  }
}

function validateFile(file) {
  const extension = file?.name?.split('.').pop()?.toLowerCase()
  if (!file || !file.size) return '不能添加空文件'
  if (isVideoFile(file)) {
    if (file.size > MAX_VIDEO_BYTES) return '单个视频不能超过 200MB'
    return ''
  }
  if (isImageFile(file)) {
    if (file.size > MAX_BYTES) return '单张图片不能超过 10MB'
    return ''
  }
  return '仅支持 jpg/png/gif 图片，或 mp4/mov/webm 视频'
}

function isVideoFile(file) {
  const extension = file?.name?.split('.').pop()?.toLowerCase()
  return VIDEO_TYPES.includes(file?.type) || (file?.type === '' && VIDEO_EXTENSIONS.includes(extension))
}

function isImageFile(file) {
  const extension = file?.name?.split('.').pop()?.toLowerCase()
  return IMAGE_TYPES.includes(file?.type)
    || (file?.type === '' && IMAGE_EXTENSIONS.includes(extension))
}

function addItem(file) {
  const key = `f${++uid}`
  const item = reactive({
    key,
    file,
    url: URL.createObjectURL(file),
    kind: isVideoFile(file) ? 'video' : 'image',
    progress: 'queued',
    mediaId: null,
    playbackUrl: null,
    playbackLowUrl: null,
    durationMs: null,
    uploadedBytes: 0,
    totalBytes: file.size,
    controller: null,
    removed: false,
    error: '',
  })
  items.value.push(item)
  processQueue()
}

function processQueue() {
  if (disposed) return
  while (activeUploads < MAX_CONCURRENT) {
    const item = items.value.find(it => it.progress === 'queued' && !it.removed)
    if (!item) break
    startUpload(item)
  }
}

async function startUpload(item) {
  activeUploads++
  item.progress = 'uploading'
  item.controller = new AbortController()
  try {
    // 图片仍走代理；视频走 E1 分片预签名直传，再轮询消费者产出转码产物。
    const uploaded = item.kind === 'video'
      ? await mediaApi.uploadVideoResumable(item.file, {
        signal: item.controller.signal,
        onProgress: (done, total) => {
          item.uploadedBytes = done
          item.totalBytes = total
        },
      })
      : await mediaApi.uploadOne(item.file, { signal: item.controller.signal })
    if (item.removed || item.controller.signal.aborted || disposed) {
      if (uploaded?.mediaId && !published) await removeOrphan(uploaded.mediaId)
      return
    }
    item.mediaId = uploaded.mediaId
    item.playbackUrl = uploaded.playbackUrl || null
    item.playbackLowUrl = uploaded.playbackLowUrl || null
    item.durationMs = uploaded.durationMs || null
    item.progress = 'processing'
    item.uploadedBytes = item.totalBytes
    const ready = await waitProcessed(item.mediaId, { signal: item.controller.signal })
    if (item.removed || item.controller.signal.aborted || disposed) {
      if (item.mediaId && !published) await removeOrphan(item.mediaId)
      return
    }
    item.playbackUrl = ready.playbackUrl || item.playbackUrl
    item.playbackLowUrl = ready.playbackLowUrl || item.playbackLowUrl
    item.durationMs = ready.durationMs || item.durationMs
    item.progress = 'ready'
    item.error = ''
  } catch (err) {
    if ((isAbortError(err) || item.removed || disposed) && item.mediaId && !published) {
      const id = item.mediaId
      item.mediaId = null
      await removeOrphan(id)
    }
    if (!isAbortError(err) && !item.removed && !disposed) {
      item.progress = 'fail'
      item.error = getErrorText(err) || (item.kind === 'video'
        ? '视频处理失败，可移除后重传'
        : '这张图处理失败，可移除后重传')
      toast(item.error, 'error')
    }
  } finally {
    activeUploads--
    item.controller = null
    processQueue()
  }
}

function removeOrphan(mediaId) {
  return mediaApi.remove(mediaId).catch(() => {})
}

function releasePreview(item) {
  if (item.url) {
    URL.revokeObjectURL(item.url)
    item.url = ''
  }
}

function removeItem(key) {
  const index = items.value.findIndex(i => i.key === key)
  if (index < 0) return
  const item = items.value[index]
  item.removed = true
  item.controller?.abort()
  releasePreview(item)
  if (item.mediaId && !published) {
    const id = item.mediaId
    item.mediaId = null
    removeOrphan(id)
  }
  items.value.splice(index, 1)
  processQueue()
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
  if (!it || it.progress !== 'fail' || !it.file) return
  if (it.mediaId) {
    const id = it.mediaId
    it.mediaId = null
    await removeOrphan(id)
  }
  it.error = ''
  it.uploadedBytes = 0
  it.removed = false
  it.progress = 'queued'
  processQueue()
}

async function submitNote() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const ordered = items.value.map(i => i.mediaId)
    await noteApi.create({
      title: title.value.trim(),
      content: content.value.trim(),
      placeName: placeName.value.trim(),
      latitude: coordinateText(latitude.value) === '' ? null : Number(latitude.value),
      longitude: coordinateText(longitude.value) === '' ? null : Number(longitude.value),
      placeSource: coordinateText(latitude.value) === '' ? null : placeSource.value,
      placePrecision: coordinateText(latitude.value) === '' ? null : placePrecision.value,
      mediaIds: ordered,
      visibility: Number(visibility.value),
      requestKey,
    })
    published = true
    requestKey = crypto.randomUUID()
    items.value.forEach(releasePreview)
    toast('发布成功')
    router.push('/')
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    submitting.value = false
  }
}

function coordinateText(value) {
  return value == null ? '' : String(value).trim()
}

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  disposed = true
  items.value.forEach(item => {
    item.removed = true
    item.controller?.abort()
    releasePreview(item)
    if (item.mediaId && !published) removeOrphan(item.mediaId)
  })
  items.value = []
})
</script>

<template>
  <section class="max-w-[640px] mx-auto pt-6">
    <h1 class="text-lg font-semibold mb-4">发布笔记</h1>

    <!-- 媒体选区：图片可多选，视频单选且不可与图片混合 -->
    <div
      class="rounded-2xl border-2 border-dashed border-line hover:border-brand-300 transition p-3 bg-surface"
      @dragover="dragOver"
      @drop="onDrop"
    >
      <div class="flex flex-wrap gap-2.5">
        <div v-for="(it, idx) in items" :key="it.key" data-testid="upload-item" class="relative w-[104px]">
          <img v-if="it.kind === 'image'" :src="it.url" alt="" class="w-[104px] h-[104px] object-cover rounded-xl" />
          <video
            v-else
            :src="it.url"
            class="w-[104px] h-[104px] object-cover rounded-xl"
            muted
            playsinline
            preload="metadata"
          ></video>
          <!-- 状态徽标 -->
          <span
            v-if="it.progress !== 'ready'"
            data-testid="badge"
            class="absolute inset-x-1 bottom-1 h-6 rounded-full text-[11px] text-white grid place-items-center backdrop-blur px-1 truncate"
            :class="it.progress === 'fail' ? 'bg-red-500/90' : 'bg-black/50'"
          >
            <template v-if="it.progress === 'queued'">等待上传…</template>
            <template v-else-if="it.progress === 'uploading'">上传中 {{ it.totalBytes ? Math.floor(it.uploadedBytes / it.totalBytes * 100) : 0 }}%</template>
            <template v-else-if="it.progress === 'processing'">
              <svg class="animate-spin -ml-0.5 mr-1 h-3 w-3" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" opacity=".25"/><path d="M22 12a10 10 0 0 1-10 10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/></svg>
              处理中
            </template>
            <template v-else>失败 · 点重传</template>
          </span>

          <!-- 操作角标 -->
          <div class="absolute top-1 right-1 flex gap-1">
            <button :aria-label="`移除${it.kind === 'video' ? '视频' : '图片'}`" class="w-5 h-5 rounded-full bg-black/55 text-white text-xs leading-none grid place-items-center" title="移除" @click="removeItem(it.key)">×</button>
          </div>
          <div class="absolute top-1 left-1 flex gap-0.5">
            <button v-if="idx > 0" aria-label="前移图片" class="w-5 h-5 rounded-full bg-black/45 text-white text-[10px]" title="前移" @click="moveItem(it.key, -1)">←</button>
            <button v-if="idx < items.length - 1" aria-label="后移图片" class="w-5 h-5 rounded-full bg-black/45 text-white text-[10px]" title="后移" @click="moveItem(it.key, 1)">→</button>
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
      <p class="mt-2 text-[11px] text-ink-soft">{{ items.length }}/{{ MAX_FILES }} 个媒体 · 支持 jpg/png/gif，或单个 mp4/mov/webm 视频 · 可拖拽进来</p>
      <input ref="fileInput" type="file" multiple :accept="ACCEPT" aria-label="选择图片或视频" class="hidden" @change="onPick" />
    </div>

    <!-- 表单 -->
    <form class="mt-4 space-y-3 bg-surface rounded-2xl border border-line p-4" @submit.prevent="submitNote">
      <div>
        <label for="publish-title" class="sr-only">标题</label>
        <input
          id="publish-title"
          v-model="title"
          data-testid="input-title"
          maxlength="64"
          placeholder="填写标题（必填）"
          class="w-full h-11 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm"
        />
        <p class="text-right text-[11px] text-ink-soft mt-0.5">{{ titleCount }}/64</p>
      </div>
      <label for="publish-content" class="sr-only">正文</label>
      <textarea
        id="publish-content"
        v-model="content"
        rows="4"
        maxlength="2000"
        placeholder="这一刻的风景与心情（选填）"
        class="w-full p-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm resize-none"
      ></textarea>
      <label for="publish-place" class="sr-only">地点</label>
      <input
        id="publish-place"
        v-model="placeName"
        maxlength="128"
        placeholder="📍 添加地点（选填）"
        class="w-full h-10 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm"
      />
      <div class="grid grid-cols-2 gap-2">
        <div>
          <label for="publish-latitude" class="sr-only">纬度</label>
          <input
            id="publish-latitude"
            v-model="latitude"
            data-testid="input-latitude"
            type="number"
            step="any"
            placeholder="纬度（可选）"
            class="w-full h-10 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm"
          />
        </div>
        <div>
          <label for="publish-longitude" class="sr-only">经度</label>
          <input
            id="publish-longitude"
            v-model="longitude"
            data-testid="input-longitude"
            type="number"
            step="any"
            placeholder="经度（可选）"
            class="w-full h-10 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm"
          />
        </div>
      </div>
      <p v-if="coordinateError" data-testid="location-error" class="text-xs text-red-500" role="alert">{{ coordinateError }}</p>
      <div v-if="hasCoordinates" class="grid grid-cols-2 gap-2">
        <label class="sr-only" for="publish-place-source">地点来源</label>
        <select id="publish-place-source" v-model="placeSource" class="h-10 px-3 rounded-xl bg-mute border border-transparent text-sm">
          <option value="MAP">地图选择</option>
          <option value="MANUAL">手工填写</option>
        </select>
        <label class="sr-only" for="publish-place-precision">地点精度</label>
        <select id="publish-place-precision" v-model="placePrecision" class="h-10 px-3 rounded-xl bg-mute border border-transparent text-sm">
          <option value="EXACT">精确</option>
          <option value="APPROXIMATE">大致位置</option>
        </select>
      </div>
      <fieldset class="flex items-center gap-4 text-sm text-ink-soft select-none">
        <legend class="sr-only">笔记可见范围</legend>
        <label class="inline-flex items-center gap-1.5 cursor-pointer">
          <input v-model="visibility" type="radio" value="1" class="accent-brand-500 w-4 h-4" />
          🌍 公开
        </label>
        <label class="inline-flex items-center gap-1.5 cursor-pointer">
          <input v-model="visibility" type="radio" value="0" class="accent-brand-500 w-4 h-4" />
          🔒 仅自己可见
        </label>
      </fieldset>
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
