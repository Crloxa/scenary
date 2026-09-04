import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: mocks,
  unwrap: value => value,
}))

import { notificationApi } from '@/api/notification'

describe('notificationApi', () => {
  it('loads a descending cursor page with unread count', async () => {
    mocks.get.mockResolvedValue({ code: 0, data: { list: [], unreadCount: 2 } })

    await notificationApi.list({ cursor: 801, limit: 10 })

    expect(mocks.get).toHaveBeenCalledWith('/notifications', {
      params: { cursor: 801, limit: 10 },
    })
  })

  it('supports both selected and all-read requests', async () => {
    mocks.post.mockResolvedValue({ code: 0, data: null })

    await notificationApi.markRead([801, 802])
    await notificationApi.markRead([])

    expect(mocks.post.mock.calls).toEqual([
      ['/notifications/read', { ids: [801, 802] }],
      ['/notifications/read', { ids: [] }],
    ])
  })
})
