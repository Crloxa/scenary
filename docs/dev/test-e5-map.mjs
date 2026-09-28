// E5 地图视野框浏览黑盒（docs/05 §15、03 附 14）
// 运行：SCENARY_API_BASE_URL=http://localhost:8081/api/v1 node docs/dev/test-e5-map.mjs
//
// 覆盖：匿名只见公开+带坐标；参数边界 40000；keyset 游标翻页；P18 屏蔽双向过滤。
// 每轮随机用户名 + 随机 1° 视野框 → 天然可重复执行，不与历史数据互相污染。
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8081/api/v1'
const ROOT = fileURLToPath(new URL('../../', import.meta.url))
const pass = []
const fail = []
const ok = (name, condition, detail = '') => {
  (condition ? pass : fail).push(name)
  console.log(`${condition ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`)
}
const headers = token => ({ Authorization: `Bearer ${token}` })
const json = async response => response.json()
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options))

async function register(username, password = 'Passw0rd!234') {
  const body = await request('/auth/register', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname: username }),
  })
  if (body.code !== 0 || !body.data?.accessToken) {
    throw new Error(`register failed code=${body.code} msg=${body.message}`)
  }
  return body.data
}

const gpsJpeg = readFileSync(`${ROOT}docs/dev/fixtures/p12-gps.jpg`)

async function uploadImage(token) {
  const form = new FormData()
  form.append('files', new Blob([gpsJpeg], { type: 'image/jpeg' }), 'e5.jpg')
  const body = await request('/media/images', { method: 'POST', headers: headers(token), body: form })
  if (body.code !== 0) throw new Error(`upload failed code=${body.code}`)
  const mediaId = body.data.items[0].mediaId
  for (let i = 0; i < 30; i += 1) {
    const state = await request(`/media/${mediaId}`, { headers: headers(token) })
    if (state.data?.status === 1) return mediaId
    if (state.data?.status === 13 || state.data?.status === 14) {
      throw new Error(`image status=${state.data.status}`)
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error('image processing timeout')
}

async function publish(token, mediaId, extra = {}) {
  const body = await request('/notes', {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({
      title: `E5验收-${Date.now()}-${Math.floor(Math.random() * 1e6)}`,
      content: '地图视野框浏览验收',
      mediaIds: [mediaId],
      visibility: 1,
      ...extra,
    }),
  })
  if (body.code !== 0) throw new Error(`publish failed code=${body.code} msg=${body.message}`)
  return body.data.id
}

const mapNotes = async (token, params) => {
  const url = `${B}/places/notes?${new URLSearchParams(params)}`
  const response = await fetch(url, token ? { headers: headers(token) } : undefined)
  return { status: response.status, body: await response.json() }
}

// ---------- 准备：随机用户 + 随机 1° 视野框 + 三条公开带坐标 / 一条私密 / 一条无坐标 ----------
const suffix = `${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6)}`
const author = await register(`e5a_${suffix}`)
const viewer = await register(`e5v_${suffix}`)
const token = author.accessToken
const viewerToken = viewer.accessToken

const lat0 = Math.round((Math.random() * 60 - 30) * 1000) / 1000
const lng0 = Math.round((Math.random() * 100 - 50) * 1000) / 1000
const bbox = { minLat: lat0, maxLat: lat0 + 1, minLng: lng0, maxLng: lng0 + 1 }

const noteIds = []
// 每篇笔记绑定独立媒体（媒体不可复用）；3 条公开带坐标 + 1 条私密 + 1 条无坐标 = 5 篇
for (let i = 0; i < 3; i += 1) {
  noteIds.push(await publish(token, await uploadImage(token), {
    placeName: `E5点${i}`,
    latitude: lat0 + 0.1 + i * 0.001,
    longitude: lng0 + 0.1 + i * 0.001,
    placeSource: 'MAP',
    placePrecision: 'EXACT',
  }))
}
await publish(token, await uploadImage(token), { visibility: 0, latitude: lat0 + 0.5, longitude: lng0 + 0.5 }) // 私密
await publish(token, await uploadImage(token), { placeName: '无坐标', latitude: null, longitude: null }) // 无坐标

// ---------- 断言 ----------
{
  const anon = await mapNotes(null, bbox)
  ok('① 匿名视野框查询 200', anon.status === 200 && anon.body.code === 0)
  const list = anon.body.data?.list ?? []
  ok('② 恰好返回 3 条公开带坐标笔记', list.length === 3
    && noteIds.every(id => list.some(n => n.id === id)), `count=${list.length}`)
  const shape = list[0]
  ok('③ 列表项契约形状', shape != null
    && ['id', 'title', 'coverUrl', 'latitude', 'longitude', 'placeName', 'authorId', 'authorNickname']
      .every(k => k in shape))
}

{
  const bad1 = await mapNotes(null, { ...bbox, minLat: bbox.maxLat, maxLat: bbox.minLat })
  ok('④ 视野框逆序 40000', bad1.status === 400 && bad1.body.code === 40000)
  const bad2 = await mapNotes(null, { ...bbox, minLat: -91 })
  ok('④ 纬度越界 40000', bad2.status === 400 && bad2.body.code === 40000)
  const bad3 = await mapNotes(null, { minLat: 0, maxLat: 1, minLng: 0 })
  ok('④ 缺参 40000', bad3.status === 400 && bad3.body.code === 40000)
  const bad4 = await mapNotes(null, { ...bbox, cursor: '!!!' })
  ok('④ 非法 cursor 40000', bad4.status === 400 && bad4.body.code === 40000)
}

{
  const p1 = await mapNotes(null, { ...bbox, limit: 2 })
  const okP1 = p1.body.data?.hasMore === true && (p1.body.data?.list ?? []).length === 2
    && typeof p1.body.data?.nextCursor === 'string' && p1.body.data.nextCursor.length > 0
  ok('⑤ limit=2 首页 hasMore + cursor', okP1)
  const p2 = await mapNotes(null, { ...bbox, limit: 2, cursor: p1.body.data.nextCursor })
  const p1Ids = (p1.body.data?.list ?? []).map(n => n.id)
  const p2Ids = (p2.body.data?.list ?? []).map(n => n.id)
  const noOverlap = p2Ids.every(id => !p1Ids.includes(id))
  ok('⑤ 第二页无重叠且包含余量', noOverlap && p2Ids.length === 1, `p2=${p2Ids.length}`)
}

{
  const block = await request(`/users/${author.userId}/block`, {
    method: 'PUT', headers: headers(viewerToken),
  })
  ok('⑥ 前置：viewer 屏蔽 author', block.code === 0)
  const blocked = await mapNotes(viewerToken, bbox)
  ok('⑥ 屏蔽后登录视角看不到被屏蔽者笔记',
    (blocked.body.data?.list ?? []).length === 0, `count=${blocked.body.data?.list?.length}`)
  const anon = await mapNotes(null, bbox)
  ok('⑥ 匿名视角不受屏蔽影响', (anon.body.data?.list ?? []).length === 3)
  await request(`/users/${author.userId}/block`, { method: 'DELETE', headers: headers(viewerToken) })
  const restored = await mapNotes(viewerToken, bbox)
  ok('⑥ 取消屏蔽后恢复', (restored.body.data?.list ?? []).length === 3)
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`)
process.exit(fail.length ? 1 : 0)
