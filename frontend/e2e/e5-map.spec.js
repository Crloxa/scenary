import { test, expect } from '@playwright/test'

// E5 地图浏览浏览器专项（docs/03 附 14 E5-04）：
// 瓦片/接口全部路由 mock（不出网、不依赖真实数据）；覆盖视野框 marker 渲染、
// marker 点击跳详情、详情小地图"在地图中查看"跳转定位、空视野文案、390px。
const fx = (a, b) => [a, b].join('-')

const authState = {
  accessToken: fx('e5-map', 'access'),
  refreshToken: fx('e5-map', 'refresh'),
  userId: 13,
  nickname: 'E5验收用户',
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

  // 默认瓦片源为高德（E5 国内可访问决策）；离线瓦片同理走 mock
  await page.route(/is\.autonavi\.com/, route => route.fulfill({
    status: 200, contentType: 'image/png', body: TILE_PNG,
  }))
  await page.route(/tile\.openstreetmap\.org/, route => route.fulfill({
    status: 200, contentType: 'image/png', body: TILE_PNG,
  }))
  await page.route('**/api/v1/notifications*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false, unreadCount: 0,
  })))
  await page.route('**/api/v1/places/reverse-geocode*', route => route.fulfill(envelope({
    placeName: null, provider: 'none', cached: false,
  })))
  await page.route('**/api/v1/places/notes*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false,
  })))
  await page.addInitScript(state => {
    localStorage.setItem('scenary-auth', JSON.stringify(state))
    window.WebSocket = class extends EventTarget {
      constructor() { super(); this.readyState = 1 }
      send() {}
      close() { this.readyState = 3 }
    }
  }, authState)
})

test('① 视野框查询渲染 marker（390px 移动端）', async ({ page }) => {
  await page.route('**/api/v1/places/notes*', route => route.fulfill(envelope({
    list: [
      { id: 901, title: '山与海', coverUrl: null, latitude: 31.231, longitude: 121.475, placeName: '外滩', authorId: 7, authorNickname: '山客' },
      { id: 902, title: '湖泊', coverUrl: null, latitude: 31.234, longitude: 121.478, placeName: '', authorId: 8, authorNickname: '湖客' },
    ],
    nextCursor: null, hasMore: false,
  })))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/map')
  await expect(page.getByTestId('map-view-canvas')).toBeVisible({ timeout: 5000 })
  await expect(page.locator('[data-testid="map-note-pin"]')).toHaveCount(2, { timeout: 5000 })
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})

test('② 点击 marker 跳转笔记详情', async ({ page }) => {
  await page.route('**/api/v1/places/notes*', route => route.fulfill(envelope({
    list: [
      { id: 901, title: '山与海', coverUrl: null, latitude: 31.231, longitude: 121.475, placeName: '外滩', authorId: 7, authorNickname: '山客' },
    ],
    nextCursor: null, hasMore: false,
  })))
  await page.route('**/api/v1/notes/901', route => route.fulfill(envelope({
    id: 901, title: '山与海', content: '', placeName: '外滩',
    latitude: 31.231, longitude: 121.475, placeSource: 'MAP', placePrecision: 'EXACT',
    visibility: 1, createdAt: Date.now(),
    author: { id: 7, nickname: '山客', avatarUrl: null },
    images: [],
    social: { liked: false, bookmarked: false, following: false, likeCount: 0, bookmarkCount: 0, followerCount: 0, followingCount: 0 },
    mine: false,
  })))
  await page.route('**/api/v1/notes/901/comments*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false,
  })))
  await page.goto('/map')
  await expect(page.locator('[data-testid="map-note-pin"]').first()).toBeVisible({ timeout: 5000 })
  await page.locator('[data-testid="map-note-pin"]').first().click()
  await expect(page).toHaveURL(/\/note\/901/)
})

test('③ 详情页"在地图中查看"跳转 /map 并带定位参数', async ({ page }) => {
  await page.route('**/api/v1/notes/901', route => route.fulfill(envelope({
    id: 901, title: '山与海', content: '', placeName: '外滩',
    latitude: 31.231, longitude: 121.475, placeSource: 'MAP', placePrecision: 'EXACT',
    visibility: 1, createdAt: Date.now(),
    author: { id: 7, nickname: '山客', avatarUrl: null },
    images: [],
    social: { liked: false, bookmarked: false, following: false, likeCount: 0, bookmarkCount: 0, followerCount: 0, followingCount: 0 },
    mine: false,
  })))
  await page.route('**/api/v1/notes/901/comments*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false,
  })))
  await page.goto('/note/901')
  await expect(page.getByTestId('mini-map-canvas')).toBeVisible({ timeout: 5000 })
  await page.getByTestId('mini-map-view-larger').click()
  await expect(page).toHaveURL(/\/map\?lat=31\.231&lng=121\.475/)
  await expect(page.getByTestId('map-view-canvas')).toBeVisible({ timeout: 5000 })
})

test('④ 空视野展示提示文案', async ({ page }) => {
  await page.goto('/map')
  await expect(page.getByTestId('map-view-canvas')).toBeVisible({ timeout: 5000 })
  await expect(page.getByTestId('map-view-empty')).toBeVisible({ timeout: 5000 })
})
