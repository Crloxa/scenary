import { test, expect } from '@playwright/test'

test('responsive visitor can reach the login form without console errors', async ({ page }) => {
  const consoleErrors = []
  const pageErrors = []
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', error => pageErrors.push(error.message))

  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/v1/feed*', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, message: 'ok', data: { list: [], nextCursor: null, hasMore: false } }),
  }))
  await page.goto('/')
  await expect(page.getByRole('link', { name: '登录 / 注册' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  await page.getByRole('link', { name: '登录 / 注册' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByRole('button', { name: '登录', exact: true }).last().click()
  await expect(page.locator('#username-error')).toContainText('请输入用户名')
  await expect(page.getByLabel('用户名')).toHaveAttribute('aria-describedby', 'username-error')

  expect(consoleErrors).toEqual([])
  expect(pageErrors).toEqual([])
})
