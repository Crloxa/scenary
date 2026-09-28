<script setup>
// E5 地图浏览页（docs/05 §15）：以地图视野框浏览全站带坐标公开笔记。
// 公开路由；瓦片源经 utils/mapTiles（默认高德 GCJ-02）；视野框查询前把 bounds
// 由瓦片坐标转回 WGS84（接口始终收发 WGS84）。moveend 防抖刷新，游标加载本视野更多。
// VITE_ENABLE_MAP=false 时整体降级为提示页（与其他地图组件同一 flag）。
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { placeApi } from '@/api/place'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'
import { TILE_URL, TILE_SUBDOMAINS, TILE_ATTRIBUTION, toTileCoords, fromTileCoords } from '@/utils/mapTiles'

const MAP_ENABLED = import.meta.env.VITE_ENABLE_MAP !== 'false'

const route = useRoute()
const router = useRouter()

const mapEl = ref(null)
const loading = ref(false)
const empty = ref(false)
const hasMore = ref(false)
const loadingMore = ref(false)

let map = null
let layer = null
let tileLayer = null
let moveTimer = null
let disposed = false
// 记录当前视野对应的 WGS84 bbox 与游标：视野移动即重置
let currentBbox = null
let cursor = null
const items = ref([])

function debounceLoad() {
  if (moveTimer) clearTimeout(moveTimer)
  moveTimer = setTimeout(loadView, 500)
}

async function loadView() {
  if (!map || disposed) return
  const bounds = map.getBounds()
  const sw = fromTileCoords(bounds.getSouth(), bounds.getWest())
  const ne = fromTileCoords(bounds.getNorth(), bounds.getEast())
  currentBbox = { minLat: sw.lat, maxLat: ne.lat, minLng: sw.lng, maxLng: ne.lng }
  cursor = null
  items.value = []
  await fetchPage(false)
}

async function loadMore() {
  if (!currentBbox || cursor == null || loadingMore.value) return
  await fetchPage(true)
}

async function fetchPage(append) {
  loading.value = !append
  loadingMore.value = append
  try {
    const page = await placeApi.mapNotes({ ...currentBbox, cursor, limit: 20 })
    if (disposed) return
    const next = append ? items.value.concat(page.list) : page.list
    items.value = next
    cursor = page.nextCursor
    hasMore.value = page.hasMore
    empty.value = next.length === 0
    renderMarkers(next)
  } catch (e) {
    if (!disposed) toast(getErrorText(e) || '地图数据加载失败', 'error')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function renderMarkers(list) {
  if (!map || disposed || !layer) return
  import('leaflet').then((mod) => {
    if (disposed || !map) return
    const L = mod.default
    layer.clearLayers()
    list.forEach((note) => {
      const tile = toTileCoords(note.latitude, note.longitude)
      const marker = L.marker([tile.lat, tile.lng], {
        icon: L.divIcon({
          className: '',
          html: `<div data-testid="map-note-pin" title="${(note.title || '').replace(/"/g, '')}" style="width:16px;height:16px;border-radius:50%;background:#1ba473;border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)"></div>`,
          iconSize: [16, 16],
          iconAnchor: [8, 8],
        }),
      })
      marker.on('click', () => router.push(`/note/${note.id}`))
      marker.addTo(layer)
    })
  })
}

onMounted(async () => {
  if (!MAP_ENABLED) return
  const L = (await import('leaflet')).default
  await import('leaflet/dist/leaflet.css')
  // 支持 /map?lat=&lng= 定位（详情页小地图跳转）；无参数用默认中心
  const qLat = Number(route.query.lat)
  const qLng = Number(route.query.lng)
  const center = Number.isFinite(qLat) && Number.isFinite(qLng) && (route.query.lat || route.query.lng)
    ? toTileCoords(qLat, qLng)
    : { lat: 31.2304, lng: 121.4737 }
  map = L.map(mapEl.value, { center: [center.lat, center.lng], zoom: 11 })
  tileLayer = L.tileLayer(TILE_URL, {
    maxZoom: 19,
    subdomains: TILE_SUBDOMAINS.split(''),
    attribution: TILE_ATTRIBUTION,
  })
  tileLayer.addTo(map)
  layer = L.layerGroup()
  layer.addTo(map)
  map.on('moveend', debounceLoad)
  loadView()
})

onUnmounted(() => {
  disposed = true
  if (moveTimer) clearTimeout(moveTimer)
  if (map) map.remove()
  map = null
  layer = null
  tileLayer = null
})
</script>

<template>
  <section class="max-w-5xl mx-auto px-4">
    <div
      v-if="!MAP_ENABLED"
      class="py-20 text-center text-sm text-ink-soft"
    >
      🗺️ 地图功能未开启（VITE_ENABLE_MAP=false）
    </div>
    <template v-else>
      <div class="relative mt-4">
        <div
          ref="mapEl"
          data-testid="map-view-canvas"
          class="h-[calc(100vh-14rem)] min-h-72 w-full rounded-2xl overflow-hidden bg-mute"
        ></div>
        <p
          v-if="empty && !loading"
          data-testid="map-view-empty"
          class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-surface/90 px-4 py-1.5 text-xs text-ink-soft shadow"
        >当前视野暂无带地点的公开笔记</p>
      </div>
      <div class="mt-3 flex items-center justify-between text-xs text-ink-soft">
        <span>🗺️ 点击圆点查看笔记 · 拖动/缩放地图浏览其他区域</span>
        <button
          v-if="hasMore"
          type="button"
          data-testid="map-view-more"
          class="h-8 px-3 rounded-full bg-mute text-ink"
          :disabled="loadingMore"
          @click="loadMore"
        >{{ loadingMore ? '加载中…' : '加载本视野更多' }}</button>
      </div>
    </template>
  </section>
</template>
