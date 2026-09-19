import { test, expect } from '@playwright/test'

// P12-E3 发布页逆地理联动浏览器专项（docs/03 附 8 E3-03/E3-04）：
// 纯路由 mock，不起真实 provider；覆盖空字段自动回填、已填不覆盖（候选条确认）、
// 空候选静默降级、390px 移动端与 console/pageerror 干净。
//
// 实测注记（WebKit/Windows，证据见 E3 证据报告）：本 spec 的 geocode 路由
// 每个用例只注册一个处理器（beforeEach 不注册、用例内注册），且断言前用平铺
// waitForTimeout 等待防抖（600ms）+ 往返完成——expect 轮询与多路由 LIFO 链
// 组合会偶发 XHR 无响应（trace status=-1）。平铺等待形态 10 连跑无回归。
const fx = (a, b) => [a, b].join('-')

const authState = {
  accessToken: fx('p12-e3', 'access'),
  refreshToken: fx('p12-e3', 'refresh'),
  userId: 9,
  nickname: 'E3验收用户',
  avatarUrl: '',
}

const envelope = data => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 0, message: 'ok', data }),
})

let consoleErrors
let pageErrors

test.beforeEach(async ({ page }) => {
  consoleErrors = []
  pageErrors = []
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })
  page.on('pageerror', err => pageErrors.push(String(err)))

  await page.route('**/api/v1/notifications*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false, unreadCount: 0,
  })))
  await page.addInitScript(state => {
    localStorage.setItem('scenary-auth', JSON.stringify(state))
  }, authState)
})

async function openPublish(page) {
  await page.goto('/publish')
  await expect(page.getByTestId('btn-publish-submit')).toBeVisible()
}

async function fillCoordinates(page, lat, lng) {
  await page.getByTestId('input-latitude').fill(lat)
  await page.getByTestId('input-longitude').fill(lng)
  // 600ms 防抖 + 网络往返 + 渲染；WebKit 无头页定时器节流留足余量
  await page.waitForTimeout(2000)
}

test('① 坐标齐备且地名为空 → 防抖后自动回填候选地名', async ({ page }) => {
  let geocodeCalls = 0
  await page.route('**/api/v1/places/reverse-geocode*', route => {
    geocodeCalls += 1
    return route.fulfill(envelope({ placeName: '候选-31.2304', provider: 'nominatim', cached: false }))
  })
  await openPublish(page)
  await fillCoordinates(page, '31.2304', '121.4737')
  await expect(page.locator('#publish-place')).toHaveValue('候选-31.2304')
  expect(geocodeCalls).toBeGreaterThanOrEqual(1)
})

test('② 地名已填 → 不覆盖，候选条经用户确认后回填', async ({ page }) => {
  await page.route('**/api/v1/places/reverse-geocode*', route => route.fulfill(envelope({
    placeName: '外滩候选', provider: 'nominatim', cached: false,
  })))
  await openPublish(page)
  await page.locator('#publish-place').fill('我的手填地名')
  await fillCoordinates(page, '31.2304', '121.4737')
  await expect(page.locator('#publish-place')).toHaveValue('我的手填地名')
  await expect(page.getByTestId('place-suggestion')).toHaveCount(1)
  await expect(page.getByTestId('place-suggestion')).toContainText('外滩候选')
  await page.getByTestId('apply-place-suggestion').click()
  await expect(page.locator('#publish-place')).toHaveValue('外滩候选')
  await expect(page.getByTestId('place-suggestion')).toHaveCount(0)
})

test('③ 空候选（placeName=null）静默降级：无候选条、无报错、手工输入可用', async ({ page }) => {
  let geocodeCalls = 0
  await page.route('**/api/v1/places/reverse-geocode*', route => {
    geocodeCalls += 1
    return route.fulfill(envelope({ placeName: null, provider: 'nominatim', cached: false }))
  })
  await openPublish(page)
  await fillCoordinates(page, '5', '5')
  expect(geocodeCalls).toBeGreaterThanOrEqual(1)
  await expect(page.getByTestId('place-suggestion')).toHaveCount(0)
  await page.locator('#publish-place').fill('手工兜底地名')
  await expect(page.locator('#publish-place')).toHaveValue('手工兜底地名')
})

test('④ 390px 移动端：联动可用且 console/pageerror 干净', async ({ page }) => {
  await page.route('**/api/v1/places/reverse-geocode*', route => route.fulfill(envelope({
    placeName: '移动端候选', provider: 'nominatim', cached: true,
  })))
  await page.setViewportSize({ width: 390, height: 844 })
  await openPublish(page)
  await fillCoordinates(page, '31.2304', '121.4737')
  await expect(page.locator('#publish-place')).toHaveValue('移动端候选')
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})
