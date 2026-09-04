import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: mocks,
  unwrap: value => value,
}))

import { commentApi } from '@/api/comment'

describe('commentApi', () => {
  it('uses ascending comment cursor and preserves nullable parentId', async () => {
    mocks.get.mockResolvedValue({ code: 0, data: { list: [] } })
    mocks.post.mockResolvedValue({ code: 0, data: { id: 7 } })

    await commentApi.list(9, { cursor: 3, limit: 20 })
    await commentApi.create(9, { content: '风景真好' })

    expect(mocks.get).toHaveBeenCalledWith('/notes/9/comments', {
      params: { cursor: 3, limit: 20 },
    })
    expect(mocks.post).toHaveBeenCalledWith('/notes/9/comments', {
      content: '风景真好', parentId: null,
    })
  })

  it('maps soft-delete to the comment endpoint', async () => {
    mocks.delete.mockResolvedValue({ code: 0, data: null })

    await commentApi.remove(17)

    expect(mocks.delete).toHaveBeenCalledWith('/comments/17')
  })
})
