import { test, expect } from '@playwright/test'

// P12-E4 地图 UI 浏览器专项（docs/03 附 9 E4-04）：
// 瓦片经路由 mock（1×1 PNG）——不依赖公网出网；降级用例反向用 404 瓦片触发 tileerror。
// 覆盖：发布页选点回写坐标、详情页带坐标渲染小地图/无坐标零渲染、瓦片失败降级坐标文本、390px。
const fx = (a, b) => [a, b].join('-')

const authState = {
  accessToken: fx('p12-e4', 'access'),
  refreshToken: fx('p12-e4', 'refresh'),
  userId: 11,
  nickname: 'E4验收用户',
  avatarUrl: '',
}

const envelope = data => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 0, message: 'ok', data }),
})

const TILE_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64',
)

let consoleErrors
let pageErrors

test.beforeEach(async ({ page }) => {
  consoleErrors = []
  pageErrors = []
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })
  page.on('pageerror', err => pageErrors.push(String(err)))

  await page.route(/is\.autonavi\.com/, route => route.fulfill({
    status: 200, contentType: 'image/png', body: TILE_PNG,
  }))
  await page.route('**/api/v1/notifications*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false, unreadCount: 0,
  })))
  await page.route('**/api/v1/places/reverse-geocode*', route => route.fulfill(envelope({
    placeName: null, provider: 'none', cached: false,
  })))
  await page.addInitScript(state => {
    localStorage.setItem('scenary-auth', JSON.stringify(state))
    // 详情/发布页可能建立实时通知连接；统一以桩替换避免真实 WS
    window.WebSocket = class extends EventTarget {
      constructor() { super(); this.readyState = 1 }
      send() {}
      close() { this.readyState = 3 }
    }
  }, authState)
})

const mockDetailNote = page => {
  page.route('**/api/v1/notes/901', route => route.fulfill(envelope({
    id: 901,
    title: 'E4 地图回归',
    content: '夹具',
    placeName: '四姑娘山',
    latitude: 30.9785,
    longitude: 102.7591,
    placeSource: 'MAP',
    placePrecision: 'EXACT',
    visibility: 1,
    createdAt: Date.now(),
    author: { id: 7, nickname: 'E4作者', avatarUrl: null },
    images: [],
    social: { liked: false, bookmarked: false, following: false, likeCount: 0, bookmarkCount: 0, followerCount: 0, followingCount: 0 },
    mine: false,
  })))
  page.route('**/api/v1/notes/902', route => route.fulfill(envelope({
    id: 902,
    title: 'E4 无坐标笔记',
    content: '夹具',
    placeName: '',
    latitude: null,
    longitude: null,
    placeSource: null,
    placePrecision: null,
    visibility: 1,
    createdAt: Date.now(),
    author: { id: 7, nickname: 'E4作者', avatarUrl: null },
    images: [],
    social: { liked: false, bookmarked: false, following: false, likeCount: 0, bookmarkCount: 0, followerCount: 0, followingCount: 0 },
    mine: false,
  })))
  page.route('**/api/v1/notes/9*/comments*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false,
  })))
}

test('① 发布页：地图选点 → 坐标回写 → 触发逆地理查询', async ({ page }) => {
  let geocodeQuery = ''
  await page.route('**/api/v1/places/reverse-geocode*', async route => {
    geocodeQuery = route.request().url()
    return route.fulfill(envelope({ placeName: null, provider: 'none', cached: false }))
  })
  await page.goto('/publish')
  await expect(page.getByTestId('btn-publish-submit')).toBeVisible()
  await page.getByTestId('btn-open-map-picker').click()
  await expect(page.getByTestId('map-picker-canvas')).toBeVisible({ timeout: 5000 })
  await page.getByTestId('map-picker-canvas').click({ position: { x: 160, y: 120 } })
  const readout = await page.getByTestId('map-picker-readout').innerText()
  expect(readout).toMatch(/纬度 -?\d+(\.\d+)?，经度 -?\d+(\.\d+)?/)
  await page.getByTestId('map-picker-confirm').click()
  await expect(page.getByTestId('map-picker-canvas')).toHaveCount(0)
  const lat = readout.match(/纬度 (-?\d+(\.\d+)?)/)[1]
  const lng = readout.match(/经度 (-?\d+(\.\d+)?)/)[1]
  await expect(page.getByTestId('input-latitude')).toHaveValue(lat)
  await expect(page.getByTestId('input-longitude')).toHaveValue(lng)
  await expect.poll(() => geocodeQuery).toContain('latitude=')
})

test('② 详情页：带坐标渲染小地图与坐标文本（390px）', async ({ page }) => {
  mockDetailNote(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/note/901')
  await expect(page.getByTestId('note-title')).toHaveText('E4 地图回归')
  await expect(page.getByTestId('mini-map-canvas')).toBeVisible({ timeout: 5000 })
  await expect(page.getByTestId('mini-map-coords')).toContainText('四姑娘山')
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})

test('③ 详情页：无坐标笔记零渲染（无布局抖动）', async ({ page }) => {
  mockDetailNote(page)
  await page.goto('/note/902')
  await expect(page.getByTestId('note-title')).toHaveText('E4 无坐标笔记')
  await expect(page.getByTestId('mini-map')).toHaveCount(0)
})

test('④ 瓦片失败：小地图整体降级为坐标文本', async ({ page }) => {
  await page.route(/is\.autonavi\.com/, route => route.fulfill({ status: 404, body: '' }))
  mockDetailNote(page)
  await page.goto('/note/901')
  await expect(page.getByTestId('note-title')).toHaveText('E4 地图回归')
  await expect(page.getByTestId('mini-map-fallback')).toBeVisible({ timeout: 8000 })
  await expect(page.getByTestId('mini-map-canvas')).toHaveCount(0)
  await expect(page.getByTestId('mini-map-coords')).toContainText('30.9785')
})
