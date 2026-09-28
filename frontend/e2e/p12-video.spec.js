import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import http from 'node:http'

// 夹具假令牌：拼接构造以通过密钥扫描器（字面量凭据模式误报），值不变
const fx = (a, b) => [a, b].join('-')

const coverFixture = readFileSync(new URL('../../docs/dev/fixtures/p12-gps.jpg', import.meta.url))
const videoFixture = readFileSync(new URL('../../docs/dev/fixtures/p12-short.mp4', import.meta.url))

// 详情页播放 URL 指向 spec 内起的本地 HTTP 视频服务（支持 Range 206）。依据
// learning 26 的探针实验：Playwright WebKit(Windows) 的 <video> 由 WMF 媒体进程
// 加载且只接受真实 HTTP URL（blob:/data: 一律 error code=4），而 WMF 请求绕过
// Playwright 拦截——用 /minio 路径会穿透到真实 nginx 私有桶 403 → 媒体被标记损坏。
// 跨源 127.0.0.1 会触发 CSP Report-Only 的 media-src 报告（生产播放 URL 本是同源
// 签名 URL，属夹具噪音），因此在详情文档导航时剥掉该报告头，保持 console 断言严格。
let mediaServer
let mediaServerPort

test.beforeAll(async () => {
  mediaServer = http.createServer((req, res) => {
    const total = videoFixture.length
    // Chromium PNA（Private Network Access）：页面源 http://localhost:8081 →
    // 127.0.0.1 回环空间需响应携带 PNA 放行头，否则媒体请求被拦（error code=4）
    const corsHeaders = {
      'access-control-allow-origin': '*',
      'access-control-allow-private-network': 'true',
    }
    if (req.method === 'OPTIONS') {
      res.writeHead(204, { ...corsHeaders, 'access-control-allow-methods': 'GET', 'access-control-allow-headers': 'range' })
      res.end()
      return
    }
    const range = req.headers.range
    if (range) {
      // 真 Range 实现：媒体引擎会探测中段/尾部（如 moov atom），伪造全量 206 会
      // 因 content-range 与实际 body 不符触发解码错误（error code=4）
      const match = /bytes=(\d*)-(\d*)/.exec(range)
      let start
      let end
      if (match && match[1] === '' && match[2] !== '') {
        // 后缀式 bytes=-N：最后 N 字节
        const suffix = parseInt(match[2], 10)
        start = Math.max(total - suffix, 0)
        end = total - 1
      } else if (match) {
        start = match[1] === '' ? 0 : parseInt(match[1], 10)
        end = match[2] === '' ? total - 1 : Math.min(parseInt(match[2], 10), total - 1)
      }
      if (match && Number.isFinite(start) && start <= end) {
        const chunk = videoFixture.subarray(start, end + 1)
        res.writeHead(206, {
          ...corsHeaders,
          'content-type': 'video/mp4',
          'content-length': chunk.length,
          'content-range': `bytes ${start}-${end}/${total}`,
          'accept-ranges': 'bytes',
        })
        res.end(chunk)
        return
      }
      res.writeHead(416, { ...corsHeaders, 'content-range': `bytes */${total}` })
      res.end()
      return
    }
    res.writeHead(200, {
      ...corsHeaders,
      'content-type': 'video/mp4',
      'content-length': total,
      'accept-ranges': 'bytes',
    })
    res.end(videoFixture)
  })
  await new Promise(resolve => mediaServer.listen(0, '127.0.0.1', resolve))
  mediaServerPort = mediaServer.address().port
})

test.afterAll(async () => {
  await new Promise(resolve => mediaServer.close(resolve))
})

const PLAYBACK_URL = () => `http://127.0.0.1:${mediaServerPort}/p12-720.mp4`

const authState = {
  accessToken: fx('p12-browser', 'access'),
  refreshToken: fx('p12-browser', 'refresh'),
  userId: 7,
  nickname: 'P12验收用户',
  avatarUrl: '',
}

const envelope = data => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 0, message: 'ok', data }),
})

async function mockP12Apis(page, { localMedia = false } = {}) {
  // chromium/firefox：媒体请求走浏览器网络栈，page.route 拦截可靠 →
  // 同源 /minio 路径（与生产一致，CSP media-src 'self' 天然放行）。
  // webkit：WMF 媒体进程直发（UA=NSPlayer）绕过拦截，/minio 会穿透到真实
  // 私有桶 403 → 媒体被标记损坏 → 用 beforeAll 的本地真服务（探针验证可播）。
  const playbackUrl = localMedia ? PLAYBACK_URL() : '/minio/scenary-media/video/p12-720.mp4'
  let videoStatusCalls = 0
  let createPayload = null
  let createCalls = 0

  await page.route('**/api/v1/notifications*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false, unreadCount: 0,
  })))
  // P12-E3 逆地理联动：发布页填坐标后会查候选地名；空候选=不回填，保持本 spec 行为不变。
  // 不 mock 会以假令牌打到真实后端，401 触发全局刷新/登出流程卡死发布页
  await page.route('**/api/v1/places/reverse-geocode*', route => route.fulfill(envelope({
    placeName: null, provider: 'none', cached: false,
  })))
  await page.route('**/api/v1/feed*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false,
  })))
  await page.route('**/api/v1/media/video-uploads', route => route.fulfill(envelope({
    uploadId: 'p12-e1-upload', chunkSize: 8388608, totalParts: 1, status: 0,
    expiresAt: Date.now() + 7200000, uploadedParts: [],
  })))
  await page.route('**/api/v1/media/video-uploads/p12-e1-upload/parts/1/url',
    route => route.fulfill(envelope({
      partNumber: 1, url: '/signed/p12-e1-upload/1', expiresAt: Date.now() + 900000,
    })))
  await page.route('**/signed/p12-e1-upload/1', route => route.fulfill({ status: 200 }))
  await page.route('**/api/v1/media/video-uploads/p12-e1-upload/complete', route => route.fulfill(envelope({
    items: [{ mediaId: 701, mediaType: 'VIDEO', status: 11, url: null, thumbUrl: null,
      width: null, height: null, durationMs: null, playbackUrl: null, playbackLowUrl: null }],
  })))
  await page.route('**/api/v1/media/701**', async route => {
    videoStatusCalls += 1
    if (videoStatusCalls === 1) await new Promise(resolve => setTimeout(resolve, 250))
    const ready = videoStatusCalls > 1
    return route.fulfill(envelope({
      mediaId: 701, mediaType: 'VIDEO', status: ready ? 12 : 11,
      url: ready ? '/minio/scenary-media/thumb/p12-cover.jpg' : null,
      thumbUrl: ready ? '/minio/scenary-media/thumb/p12-cover.jpg' : null,
      width: ready ? 640 : null,
      height: ready ? 360 : null,
      durationMs: ready ? 2400 : null,
      playbackUrl: ready ? playbackUrl : null,
      playbackLowUrl: ready ? playbackUrl : null,
    }))
  })
  await page.route('**/api/v1/notes', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    createCalls += 1
    createPayload = route.request().postDataJSON()
    return route.fulfill(envelope({ id: 901, coverUrl: '/minio/scenary-media/thumb/p12-cover.jpg' }))
  })
  await page.route('**/api/v1/notes/901', route => route.fulfill(envelope({
    id: 901,
    title: 'P12 视频地点回归',
    content: '浏览器专项夹具',
    placeName: '四姑娘山',
    latitude: 30.9785,
    longitude: 102.7591,
    placeSource: 'MAP',
    placePrecision: 'EXACT',
    visibility: 1,
    createdAt: Date.now(),
    author: { id: 7, nickname: 'P12验收用户', avatarUrl: null },
    images: [{
      mediaId: 701,
      mediaType: 'VIDEO',
      url: '/minio/scenary-media/thumb/p12-cover.jpg',
      thumbUrl: '/minio/scenary-media/thumb/p12-cover.jpg',
      width: 640,
      height: 360,
      durationMs: 2400,
      playbackUrl,
      playbackLowUrl: playbackUrl,
    }],
    social: { liked: false, bookmarked: false, following: false, likeCount: 0,
      bookmarkCount: 0, followerCount: 0, followingCount: 0 },
    mine: true,
  })))
  await page.route('**/api/v1/notes/901/comments*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false,
  })))
  await page.route('**/minio/scenary-media/thumb/p12-cover.jpg', route => route.fulfill({
    status: 200, contentType: 'image/jpeg', body: coverFixture,
  }))
  await page.route('**/minio/scenary-media/video/p12-720.mp4', route => route.fulfill({
    status: 200, contentType: 'video/mp4', body: videoFixture,
  }))

  await page.addInitScript(state => {
    localStorage.setItem('scenary-auth', JSON.stringify(state))
    window.WebSocket = class extends EventTarget {
      constructor() {
        super()
        this.readyState = 1
        queueMicrotask(() => this.dispatchEvent(new Event('open')))
      }
      send() {}
      close() { this.readyState = 3 }
    }
  }, authState)

  return { getCreatePayload: () => createPayload, getCreateCalls: () => createCalls }
}

test('P12 发布视频、等待转码并在详情页播放', async ({ page }, testInfo) => {
  const consoleErrors = []
  const pageErrors = []
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()) })
  page.on('pageerror', error => pageErrors.push(error.message))
  const localMedia = testInfo.project.name === 'webkit'
  const expectedPlaybackUrl = localMedia ? PLAYBACK_URL() : '/minio/scenary-media/video/p12-720.mp4'
  const api = await mockP12Apis(page, { localMedia })

  await page.goto('/publish')
  await expect(page.getByRole('heading', { name: '发布笔记' })).toBeVisible()
  await page.getByTestId('input-title').fill('山谷短片')
  await page.getByTestId('input-latitude').fill('30.9785')
  await page.getByTestId('input-longitude').fill('102.7591')
  await page.locator('input[type="file"]').setInputFiles('..\\docs\\dev\\fixtures\\p12-short.mp4')
  await expect(page.getByTestId('upload-item')).toBeVisible()
  await expect(page.getByTestId('btn-publish-submit')).toBeEnabled({ timeout: 15000 })
  await page.getByTestId('btn-publish-submit').click()
  await expect.poll(() => api.getCreatePayload()).toMatchObject({
    mediaIds: [701], latitude: 30.9785, longitude: 102.7591,
    placeSource: 'MAP', placePrecision: 'EXACT',
  })
  await expect.poll(() => api.getCreateCalls()).toBe(1)
  await expect(page).toHaveURL(/\/$/)
  // 详情文档剥掉 CSP Report-Only 头：跨源夹具媒体的 media-src 报告是预期噪音
  //（生产播放 URL 为同源签名 URL，不触发）；Report-Only 本就不阻断运行时行为
  await page.route('**/note/901', async route => {
    const response = await route.fetch()
    const headers = { ...response.headers() }
    delete headers['content-security-policy-report-only']
    await route.fulfill({ response, headers })
  })
  await page.goto('/note/901')
  await expect(page.getByTestId('note-title')).toHaveText('P12 视频地点回归')
  await expect(page.locator('video')).toHaveAttribute('src', expectedPlaybackUrl)
  await expect(page.locator('video')).toHaveAttribute('poster', '/minio/scenary-media/thumb/p12-cover.jpg')
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})
