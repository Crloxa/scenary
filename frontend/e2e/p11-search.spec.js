import { test, expect } from '@playwright/test'

test('search route supports query, highlight, sort and shareable navigation', async ({ page }) => {
  const consoleErrors = []
  const pageErrors = []
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.setViewportSize({ width: 1280, height: 844 })

  await page.route('**/api/v1/search/notes*', async route => {
    const url = new URL(route.request().url())
    const q = url.searchParams.get('q')
    const isMountain = q?.toLowerCase() === 'mountain'
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, message: 'ok', data: {
        list: [{
          id: isMountain ? 12 : 11,
          title: isMountain ? 'Mountain Trail' : 'Cloud Sea',
          contentPreview: 'A quiet landscape',
          coverUrl: 'data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=',
          mediaCount: 1,
          author: { id: 7, nickname: 'Scenary作者' },
          createdAt: Date.now(),
          social: { liked: false, bookmarked: false, following: false, likeCount: 0, bookmarkCount: 0, followerCount: 0, followingCount: 0 },
          highlight: { title: isMountain ? 'Mountain Trail' : 'Cloud Sea', content: null, placeName: null, author: null },
        }],
        nextCursor: null,
        hasMore: false,
      } }),
    })
  })

  await page.goto('/search?q=cloud&sort=relevance')
  await expect(page.getByTestId('search-results')).toContainText('Cloud Sea')
  await expect(page.getByTestId('note-highlight')).toContainText('Cloud Sea')

  await page.locator('[data-testid="nav-search"] input').fill('mountain')
  await page.locator('[data-testid="nav-search"] input').press('Enter')
  await expect(page).toHaveURL(/\/search\?q=mountain$/)
  await expect(page.getByTestId('search-results')).toContainText('Mountain Trail')
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})
