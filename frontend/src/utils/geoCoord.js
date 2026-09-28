// WGS84 ↔ GCJ-02 坐标转换（E5 国内瓦片源决策，docs/05 §15）。
// 高德瓦片为 GCJ-02 坐标系，库内坐标为 WGS84（EXIF GPS 语义）：
// 展示层 WGS84→GCJ-02，选点/查询反向 GCJ-02→WGS84，接口始终收发 WGS84。
// 正向为公开通用算法；逆向用三次逼近回代，往返误差 <1e-4°（见 tests/geoCoord.spec.js）。
const PI = Math.PI
const SEMI_MAJOR = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lat, lng) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0
  ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0
  return ret
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0
  ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0
  ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0
  return ret
}

export function wgs84ToGcj02(lat, lng) {
  if (outOfChina(lat, lng)) return { lat, lng }
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = lat / 180.0 * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / ((SEMI_MAJOR * (1 - EE)) / (magic * sqrtMagic) * PI)
  dLng = (dLng * 180.0) / (SEMI_MAJOR / sqrtMagic * Math.cos(radLat) * PI)
  return { lat: lat + dLat, lng: lng + dLng }
}

export function gcj02ToWgs84(lat, lng) {
  if (outOfChina(lat, lng)) return { lat, lng }
  let wgsLat = lat
  let wgsLng = lng
  for (let i = 0; i < 3; i += 1) {
    const forward = wgs84ToGcj02(wgsLat, wgsLng)
    wgsLat += lat - forward.lat
    wgsLng += lng - forward.lng
  }
  return { lat: wgsLat, lng: wgsLng }
}
