import { test, expect } from '@playwright/test'

const authState = {
  accessToken: 'p10-browser-access',
  refreshToken: 'p10-browser-refresh',
  userId: 7,
  nickname: '验收用户',
  avatarUrl: '',
}

async function mockP10Apis(page) {
  await page.route('**/api/v1/notes/130', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, message: 'ok', data: {
      id: 130,
      title: 'P10 跨浏览器详情',
      content: '跨浏览器与键盘验收夹具',
      author: { id: 11, nickname: '作者', avatarUrl: null },
      createdAt: Date.now(),
      placeName: null,
      images: [],
      mine: false,
      social: { liked: false, bookmarked: false, following: false, likeCount: 0, bookmarkCount: 0, followerCount: 0, followingCount: 0 },
    } }),
  }))
  await page.route('**/api/v1/notes/130/comments*', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, message: 'ok', data: { list: [], nextCursor: null, hasMore: false } }),
  }))
  await page.route('**/api/v1/notifications*', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, message: 'ok', data: { list: [], nextCursor: null, hasMore: false, unreadCount: 0 } }),
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
      close() {
        this.readyState = 3
        this.dispatchEvent(new Event('close'))
      }
    }
  }, authState)
}

test('P10 detail and notification routes expose keyboard and screen-reader semantics', async ({ page }) => {
  await mockP10Apis(page)
  await page.goto('/note/130')

  await expect(page.getByRole('heading', { name: '评论' })).toBeVisible()
  const commentInput = page.getByLabel('评论内容')
  await expect(commentInput).toHaveAccessibleName('评论内容')
  await commentInput.fill('键盘测试')
  await commentInput.focus()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: '发送' })).toBeFocused()

  await page.goto('/notifications')
  await expect(page.getByRole('heading', { name: '通知' })).toBeVisible()
  await expect(page.getByRole('button', { name: '全部已读' })).toBeDisabled()
  await expect(page.locator('main')).toBeVisible()
})
