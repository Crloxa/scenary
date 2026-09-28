// 地图瓦片源配置（E5 国内可访问决策，docs/05 §15）：
// 默认高德矢量瓦片（免 key、国内直连、GCJ-02 坐标系）；
// VITE_TILE_URL 可整体覆盖模板（如天地图，需自带 tk），VITE_TILE_GCJ02=false
// 表示所选瓦片为 WGS84 系（此时坐标不再做偏移转换）。
import { wgs84ToGcj02, gcj02ToWgs84 } from '@/utils/geoCoord'

const DEFAULT_TILE_URL =
  'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}'

export const TILE_URL = import.meta.env.VITE_TILE_URL || DEFAULT_TILE_URL
export const TILE_SUBDOMAINS = '1234'
export const TILE_ATTRIBUTION = '© 高德地图'
export const TILE_IS_GCJ02 = import.meta.env.VITE_TILE_GCJ02 !== 'false'

/** 库内 WGS84 → 瓦片坐标系（marker/center 定位用） */
export function toTileCoords(lat, lng) {
  return TILE_IS_GCJ02 ? wgs84ToGcj02(lat, lng) : { lat, lng }
}

/** 瓦片坐标系 → 库内 WGS84（选点回写/视野框查询用） */
export function fromTileCoords(lat, lng) {
  return TILE_IS_GCJ02 ? gcj02ToWgs84(lat, lng) : { lat, lng }
}
