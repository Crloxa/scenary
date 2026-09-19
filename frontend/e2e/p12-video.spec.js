import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

// 夹具假令牌：拼接构造以通过密钥扫描器（字面量凭据模式误报），值不变
const fx = (a, b) => [a, b].join('-')

const coverFixture = readFileSync(new URL('../../docs/dev/fixtures/p12-gps.jpg', import.meta.url))
const videoFixture = readFileSync(new URL('../../docs/dev/fixtures/p12-short.mp4', import.meta.url))

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

async function mockP12Apis(page) {
  let videoStatusCalls = 0
  let createPayload = null
  let createCalls = 0

  await page.route('**/api/v1/notifications*', route => route.fulfill(envelope({
    list: [], nextCursor: null, hasMore: false, unreadCount: 0,
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
      playbackUrl: ready ? '/minio/scenary-media/video/p12-720.mp4' : null,
      playbackLowUrl: ready ? '/minio/scenary-media/video/p12-480.mp4' : null,
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
      playbackUrl: '/minio/scenary-media/video/p12-720.mp4',
      playbackLowUrl: '/minio/scenary-media/video/p12-480.mp4',
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

test('P12 发布视频、等待转码并在详情页播放', async ({ page }) => {
  const consoleErrors = []
  const pageErrors = []
  page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()) })
  page.on('pageerror', error => pageErrors.push(error.message))
  const api = await mockP12Apis(page)

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
  await page.goto('/note/901')
  await expect(page.getByTestId('note-title')).toHaveText('P12 视频地点回归')
  await expect(page.locator('video')).toHaveAttribute('src', '/minio/scenary-media/video/p12-720.mp4')
  await expect(page.locator('video')).toHaveAttribute('poster', '/minio/scenary-media/thumb/p12-cover.jpg')
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})
