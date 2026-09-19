<script setup>
// P17 图片编辑器弹窗（docs/05 §13）：比例居中裁剪 + 90° 旋转 + CSS 滤镜预设，
// canvas 导出 JPEG 后交回发布页走既有上传管线。无网络依赖，可经 flag 整体摘除。
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  FILTERS, ASPECTS, computeCrop, normalizeRotation, exportSize, exportFileName,
} from '@/utils/imageEditor'

const props = defineProps({
  file: { type: File, default: null },
})
const emit = defineEmits(['apply', 'close'])

const objectUrl = ref('')
const image = ref(null)
const imageReady = ref(false)
const rotation = ref(0)
const aspectId = ref('free')
const filterId = ref('none')
const applying = ref(false)
const previewCanvas = ref(null)

const activeFilter = computed(() => FILTERS.find(f => f.id === filterId.value) ?? FILTERS[0])
const previewStyle = computed(() => ({ filter: activeFilter.value.css }))
const previewClass = computed(() => ({
  'rotate-90': rotation.value === 90,
  'rotate-180': rotation.value === 180,
  'rotate-270': rotation.value === 270,
}))
const canApply = computed(() => imageReady.value && !applying.value)

function rotate() {
  rotation.value = normalizeRotation(rotation.value + 90)
}

function drawPreview() {
  const img = image.value
  const canvas = previewCanvas.value
  if (!img || !canvas) return
  const crop = computeCrop(img.naturalWidth, img.naturalHeight, aspectId.value)
  const box = canvas.parentElement
  const scale = Math.min((box?.clientWidth ?? 320) / crop.w, 320 / crop.h)
  canvas.width = crop.w
  canvas.height = crop.h
  canvas.style.width = `${Math.max(64, Math.floor(crop.w * scale))}px`
  canvas.style.height = `${Math.max(64, Math.floor(crop.h * scale))}px`
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, crop.x, crop.y, crop.w, crop.h, 0, 0, crop.w, crop.h)
}

async function load() {
  imageReady.value = false
  image.value = null
  if (!props.file) return
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = URL.createObjectURL(props.file)
  const img = new Image()
  img.onload = () => {
    image.value = img
    imageReady.value = true
    drawPreview()
  }
  img.onerror = () => {
    imageReady.value = false
  }
  img.src = objectUrl.value
}

async function apply() {
  const img = image.value
  if (!img || applying.value) return
  applying.value = true
  try {
    const crop = computeCrop(img.naturalWidth, img.naturalHeight, aspectId.value)
    const { width, height } = exportSize(img.naturalWidth, img.naturalHeight, aspectId.value, rotation.value)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (activeFilter.value.css !== 'none') ctx.filter = activeFilter.value.css
    ctx.translate(width / 2, height / 2)
    ctx.rotate((normalizeRotation(rotation.value) * Math.PI) / 180)
    ctx.drawImage(img, crop.x, crop.y, crop.w, crop.h, -crop.w / 2, -crop.h / 2, crop.w, crop.h)
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9))
    if (blob) {
      emit('apply', new File([blob], exportFileName(props.file?.name), { type: 'image/jpeg' }))
    }
  } finally {
    applying.value = false
  }
}

watch(aspectId, () => drawPreview())
watch(() => props.file, () => load())
onMounted(load)
onUnmounted(() => {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
})
</script>

<template>
  <div
    class="fixed inset-0 z-50 bg-black/50 grid place-items-center px-4"
    data-testid="image-editor"
    @click.self="emit('close')"
  >
    <div class="w-full max-w-md bg-surface rounded-2xl p-4 space-y-3 max-h-[92vh] overflow-y-auto">
      <div class="flex items-center justify-between">
        <h2 class="font-medium">编辑图片</h2>
        <button class="text-ink-soft hover:text-ink text-lg leading-none" aria-label="关闭编辑器" @click="emit('close')">×</button>
      </div>

      <!-- 预览区：canvas 绘制裁剪结果，CSS 旋转 + 滤镜实时预览 -->
      <div class="grid place-items-center bg-mute rounded-xl p-3 min-h-[220px]">
        <p v-if="!imageReady" class="text-xs text-ink-soft">图片加载中…</p>
        <canvas
          v-show="imageReady"
          ref="previewCanvas"
          data-testid="editor-preview"
          class="rounded-lg transition-transform"
          :class="previewClass"
          :style="previewStyle"
          aria-label="编辑预览"
        ></canvas>
      </div>

      <!-- 比例裁剪 -->
      <div>
        <p class="text-xs text-ink-soft mb-1">裁剪比例</p>
        <div class="flex flex-wrap gap-1.5" role="group" aria-label="裁剪比例">
          <button
            v-for="a in ASPECTS"
            :key="a.id"
            type="button"
            class="h-8 px-3 rounded-full border text-xs transition"
            :class="aspectId === a.id ? 'border-brand-500 bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300' : 'border-line text-ink-soft hover:border-brand-200'"
            :aria-pressed="aspectId === a.id"
            @click="aspectId = a.id"
          >{{ a.label }}</button>
          <button
            type="button"
            class="h-8 px-3 rounded-full border border-line text-xs text-ink-soft hover:border-brand-200"
            aria-label="旋转 90 度"
            @click="rotate"
          >↻ 旋转</button>
        </div>
      </div>

      <!-- 滤镜预设 -->
      <div>
        <p class="text-xs text-ink-soft mb-1">滤镜</p>
        <div class="flex flex-wrap gap-1.5" role="group" aria-label="滤镜">
          <button
            v-for="f in FILTERS"
            :key="f.id"
            type="button"
            :data-testid="`filter-${f.id}`"
            class="h-8 px-3 rounded-full border text-xs transition"
            :class="filterId === f.id ? 'border-brand-500 bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300' : 'border-line text-ink-soft hover:border-brand-200'"
            :aria-pressed="filterId === f.id"
            @click="filterId = f.id"
          >{{ f.name }}</button>
        </div>
      </div>

      <div class="flex gap-2 pt-1">
        <button
          type="button"
          class="flex-1 h-10 rounded-xl border border-line text-ink-soft text-sm"
          @click="emit('close')"
        >取消</button>
        <button
          type="button"
          data-testid="editor-apply"
          :disabled="!canApply"
          class="flex-1 h-10 rounded-xl bg-brand-500 text-white text-sm disabled:bg-neutral-200 disabled:text-neutral-400"
          @click="apply"
        >{{ applying ? '生成中…' : '使用这张' }}</button>
      </div>
    </div>
  </div>
</template>
