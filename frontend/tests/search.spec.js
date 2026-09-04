import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: mocks,
  unwrap: value => value,
}))

import { searchApi } from '@/api/search'

describe('searchApi', () => {
  it('passes query, sort, limit and opaque cursor to the search endpoint', async () => {
    mocks.get.mockResolvedValue({ code: 0, data: { list: [] } })

    await searchApi.notes({ q: '云海', cursor: 'opaque-cursor', limit: 20, sort: 'relevance' })

    expect(mocks.get).toHaveBeenCalledWith('/search/notes', {
      params: { q: '云海', cursor: 'opaque-cursor', limit: 20, sort: 'relevance' },
    })
  })

  it('does not send a cursor for the first page', async () => {
    mocks.get.mockResolvedValue({ code: 0, data: { list: [] } })

    await searchApi.notes({ q: '山海' })

    expect(mocks.get).toHaveBeenCalledWith('/search/notes', {
      params: { q: '山海', limit: 10, sort: 'recent' },
    })
  })
})
