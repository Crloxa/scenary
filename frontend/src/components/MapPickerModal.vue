<script setup>
// P12-E4 发布选点弹窗（docs/05 §6.6）：leaflet 经本组件的动态导入拆包——
// flag 关闭或未打开弹窗时主包不含地图代码。点击地图/拖动 marker 更新候选坐标，
// 确认后回传父级写入 latitude/longitude（place_source=MAP）并触发 E3 候选地名。
// 键盘/无瓦片替代路径：父级的手工坐标输入始终保留；瓦片失败时弹窗内提示并可继续点选。
import { onMounted, onUnmounted, ref, reactive } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = defineProps({
  latitude: { type: [Number, String], default: null },
  longitude: { type: [Number, String], default: null },
})
const emit = defineEmits(['pick', 'close'])

const mapEl = ref(null)
const DEFAULT_LAT = 31.2304
const DEFAULT_LNG = 121.4737
// 空字符串/无效值回落默认（Number('') 是 0，会把初始点定到 (0,0)）
const toCoord = (v, fallback) => {
  const n = v == null || v === '' ? NaN : Number(v)
  return Number.isFinite(n) ? n : fallback
}
const picked = reactive({
  lat: toCoord(props.latitude, DEFAULT_LAT),
  lng: toCoord(props.longitude, DEFAULT_LNG),
})
const tilesFailed = ref(false)

let map = null
let marker = null
let tileErrorCount = 0
let tileLayer = null

// divIcon 避免 bundler 下默认 marker 图片 404（L.Icon.Default 已知问题）
const pinIcon = L.divIcon({
  className: '',
  html: '<div data-testid="map-marker" style="width:18px;height:18px;border-radius:50% 50% 50% 0;background:#1ba473;transform:rotate(-45deg);border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></div>',
  iconSize: [18, 18],
  iconAnchor: [9, 18],
})

function setPicked(lat, lng) {
  picked.lat = Number(Number(lat).toFixed(6))
  picked.lng = Number(Number(lng).toFixed(6))
  if (marker) marker.setLatLng([picked.lat, picked.lng])
}

onMounted(() => {
  map = L.map(mapEl.value, { center: [picked.lat, picked.lng], zoom: 13 })
  tileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  })
  tileLayer.on('tileerror', () => {
    tileErrorCount += 1
    if (tileErrorCount >= 3) tilesFailed.value = true
  })
  tileLayer.addTo(map)
  marker = L.marker([picked.lat, picked.lng], { icon: pinIcon, draggable: true })
  marker.addTo(map)
  marker.on('dragend', () => {
    const { lat, lng } = marker.getLatLng()
    setPicked(lat, lng)
  })
  map.on('click', event => setPicked(event.latlng.lat, event.latlng.lng))
})

onUnmounted(() => {
  if (map) map.remove()
  map = null
  marker = null
  tileLayer = null
})

function confirmPick() {
  emit('pick', { latitude: picked.lat, longitude: picked.lng })
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-3"
    role="dialog"
    aria-modal="true"
    aria-label="地图选点"
    @click.self="emit('close')"
  >
    <div class="w-full max-w-lg rounded-2xl bg-surface p-4 shadow-xl">
      <div class="flex items-center justify-between mb-2">
        <h2 class="text-sm font-medium text-ink">地图选点</h2>
        <button
          type="button"
          data-testid="map-picker-close"
          class="h-8 w-8 rounded-full bg-mute text-ink"
          aria-label="关闭地图选点"
          @click="emit('close')"
        >✕</button>
      </div>
      <div
        ref="mapEl"
        data-testid="map-picker-canvas"
        class="h-64 w-full rounded-xl overflow-hidden bg-mute"
      ></div>
      <p v-if="tilesFailed" data-testid="map-tiles-fallback" class="mt-2 text-xs text-ink-soft" role="alert">
        瓦片加载失败（离线或出网受限）：仍可点选/拖动，或直接使用手工坐标输入
      </p>
      <p class="mt-2 text-xs text-ink-soft" data-testid="map-picker-readout">
        已选位置：纬度 {{ picked.lat }}，经度 {{ picked.lng }}
      </p>
      <div class="mt-3 flex gap-2">
        <button
          type="button"
          data-testid="map-picker-confirm"
          class="h-10 flex-1 rounded-xl bg-brand-500 text-white text-sm"
          @click="confirmPick"
        >使用此位置</button>
        <button
          type="button"
          data-testid="map-picker-cancel"
          class="h-10 flex-1 rounded-xl bg-mute text-ink text-sm"
          @click="emit('close')"
        >取消</button>
      </div>
    </div>
  </div>
</template>
