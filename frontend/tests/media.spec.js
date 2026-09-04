import { describe, expect, it, vi } from 'vitest'

import { isAbortError, mediaApi, waitProcessed } from '@/api/media'

describe('media upload state helpers', () => {
  it('polls until processing completes', async () => {
    vi.useFakeTimers()
    const statusSpy = vi.spyOn(mediaApi, 'status')
      .mockResolvedValueOnce({ status: 0 })
      .mockResolvedValueOnce({ status: 1, url: '/thumb.jpg' })

    const pending = waitProcessed(99, { intervalMs: 20, maxTries: 3 })
    await vi.advanceTimersByTimeAsync(20)

    await expect(pending).resolves.toMatchObject({ status: 1, url: '/thumb.jpg' })
    expect(statusSpy).toHaveBeenCalledTimes(2)
  })

  it('exposes server-side image and video failures immediately', async () => {
    vi.spyOn(mediaApi, 'status').mockResolvedValueOnce({ status: 2 })
    await expect(waitProcessed(7, { intervalMs: 1, maxTries: 1 })).rejects.toThrow('媒体处理失败')

    mediaApi.status.mockResolvedValueOnce({ status: 13 })
    await expect(waitProcessed(8, { intervalMs: 1, maxTries: 1 })).rejects.toThrow('媒体处理失败')
  })

  it('cancels before issuing a status request', async () => {
    const controller = new AbortController()
    controller.abort()
    await expect(waitProcessed(5, { signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' })
    expect(isAbortError(new Error('x'))).toBe(false)
  })
})
