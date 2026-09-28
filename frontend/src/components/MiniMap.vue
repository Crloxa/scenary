<script setup>
// P12-E4 详情页小地图（docs/05 §6.6）：仅带坐标笔记渲染（父级 v-if，无坐标零布局抖动）。
// 只读展示：禁拖拽/缩放，保留 OSM attribution；坐标文本常驻展示（可访问性的文本替代），
// 瓦片加载失败时降级为纯坐标文本（05 §6.6 未准备瓦片时组件整体降级）。
import { onMounted, onUnmounted, ref } from 'vue'

// leaflet 在 onMounted 内动态 import：模块加载零 window 依赖——
// leaflet UMD 加载即做 Browser 检测，若在模块作用域静态 import，
// 任何"组件模块被加载但环境已拆（vitest teardown）"的时序都会崩；
// 惰性加载后 chunk 拆分不变（leaflet 仍为独立 chunk，挂载时才拉取）
const props = defineProps({
  latitude: { type: [Number, String], required: true },
  longitude: { type: [Number, String], required: true },
  placeName: { type: String, default: '' },
})

const mapEl = ref(null)
const tilesFailed = ref(false)
let map = null
let tileLayer = null
let tileErrorCount = 0

const lat = () => Number(props.latitude)
const lng = () => Number(props.longitude)

onMounted(async () => {
  const L = (await import('leaflet')).default
  await import('leaflet/dist/leaflet.css')
  map = L.map(mapEl.value, {
    center: [lat(), lng()],
    zoom: 14,
    dragging: false,
    scrollWheelZoom: false,
    doubleClickZoom: false,
    boxZoom: false,
    keyboard: false,
    zoomControl: false,
    attributionControl: true,
  })
  tileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  })
  tileLayer.on('tileerror', () => {
    tileErrorCount += 1
    if (tileErrorCount >= 3) tilesFailed.value = true
  })
  tileLayer.addTo(map)
  L.marker([lat(), lng()], {
    icon: L.divIcon({
      className: '',
      html: '<div style="width:14px;height:14px;border-radius:50%;background:#1ba473;border:2px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,.4)"></div>',
      iconSize: [14, 14],
      iconAnchor: [7, 7],
    }),
  }).addTo(map)
})

onUnmounted(() => {
  if (map) map.remove()
  map = null
  tileLayer = null
})
</script>

<template>
  <figure class="mt-3" data-testid="mini-map">
    <div
      v-if="!tilesFailed"
      ref="mapEl"
      data-testid="mini-map-canvas"
      class="h-40 w-full rounded-xl overflow-hidden bg-mute"
    ></div>
    <figcaption data-testid="mini-map-coords" class="mt-1 text-xs text-ink-soft">
      📍 {{ placeName || '坐标位置' }}（纬度 {{ lat() }}，经度 {{ lng() }}）
      <span v-if="tilesFailed" data-testid="mini-map-fallback">· 瓦片不可用，已降级为坐标文本</span>
    </figcaption>
  </figure>
</template>
