import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  get: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: mocks,
  unwrap: value => value,
}))

import { socialApi } from '@/api/social'

describe('socialApi', () => {
  it('maps toggle state to PUT and DELETE endpoints', async () => {
    mocks.request.mockResolvedValue({ code: 0, data: 'ok' })

    await socialApi.like(9, true)
    await socialApi.bookmark(9, false)
    await socialApi.follow(11, true)

    expect(mocks.request.mock.calls).toEqual([
      [{ method: 'put', url: '/notes/9/like' }],
      [{ method: 'delete', url: '/notes/9/bookmark' }],
      [{ method: 'put', url: '/users/11/follow' }],
    ])
  })

  it('passes bookmark cursor and limit as query parameters', async () => {
    mocks.get.mockResolvedValue({ code: 0, data: { list: [] } })

    await socialApi.bookmarks({ cursor: 30, limit: 20 })

    expect(mocks.get).toHaveBeenCalledWith('/users/me/bookmarks', {
      params: { cursor: 30, limit: 20 },
    })
  })
})
