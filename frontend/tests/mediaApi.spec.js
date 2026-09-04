import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: mocks,
  unwrap: value => value.data ?? value,
}))

import { mediaApi } from '@/api/media'
import { noteApi } from '@/api/note'

describe('P12 media and location API adapters', () => {
  it('unwraps the single video item from the upload envelope', async () => {
    mocks.post.mockResolvedValue({
      code: 0,
      data: { items: [{ mediaId: 701, mediaType: 'VIDEO', status: 11 }] },
    })
    const file = new File(['video'], 'clip.mp4', { type: 'video/mp4' })

    await expect(mediaApi.uploadVideo(file)).resolves.toMatchObject({
      mediaId: 701, mediaType: 'VIDEO', status: 11,
    })
    expect(mocks.post).toHaveBeenCalledWith('/media/videos', expect.any(FormData), {})
  })

  it('sends nullable location fields using the note contract', async () => {
    mocks.post.mockResolvedValue({ code: 0, data: { id: 88 } })

    await noteApi.create({
      title: '山谷短片',
      mediaIds: [701],
      latitude: 30.9785,
      longitude: 102.7591,
      placeSource: 'MAP',
      placePrecision: 'EXACT',
    })

    expect(mocks.post.mock.calls.at(-1)[1]).toMatchObject({
      title: '山谷短片',
      content: '',
      placeName: '',
      latitude: 30.9785,
      longitude: 102.7591,
      placeSource: 'MAP',
      placePrecision: 'EXACT',
      mediaIds: [701],
      visibility: 1,
      requestKey: null,
    })
  })
})
