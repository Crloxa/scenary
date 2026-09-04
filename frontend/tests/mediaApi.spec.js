import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn(), delete: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: mocks,
  unwrap: value => value.data ?? value,
}))

import { mediaApi } from '@/api/media'
import { noteApi } from '@/api/note'

beforeEach(() => {
  mocks.post.mockReset()
  mocks.get.mockReset()
  mocks.delete.mockReset()
  localStorage.clear()
})

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

  it('uploads a video chunk directly and clears the resumable session after complete', async () => {
    mocks.post
      .mockResolvedValueOnce({ code: 0, data: {
        uploadId: 'u-1', chunkSize: 8, totalParts: 1, status: 0,
        expiresAt: Date.now() + 7_200_000, uploadedParts: [],
      } })
      .mockResolvedValueOnce({ code: 0, data: {
        partNumber: 1, url: '/signed/u-1/1', expiresAt: Date.now() + 900_000,
      } })
      .mockResolvedValueOnce({ code: 0, data: {
        items: [{ mediaId: 703, mediaType: 'VIDEO', status: 11 }],
      } })
    const put = vi.fn().mockResolvedValue({ ok: true, status: 200 })
    vi.stubGlobal('fetch', put)
    const file = new File(['video'], 'clip.mp4', { type: 'video/mp4' })

    await expect(mediaApi.uploadVideoResumable(file)).resolves.toMatchObject({ mediaId: 703 })

    expect(put).toHaveBeenCalledWith('/signed/u-1/1', expect.objectContaining({ method: 'PUT' }))
    expect(mocks.post.mock.calls[0][0]).toBe('/media/video-uploads')
    expect(mocks.post.mock.calls[1][0]).toBe('/media/video-uploads/u-1/parts/1/url')
    expect(mocks.post.mock.calls[2][0]).toBe('/media/video-uploads/u-1/complete')
    expect(localStorage.getItem('scenary-video-upload-session')).toBeNull()
    vi.unstubAllGlobals()
  })

  it('resumes the same file from stored session and skips an uploaded chunk', async () => {
    const file = new File(['abc'], 'resume.mp4', { type: 'video/mp4', lastModified: 0 })
    localStorage.setItem('scenary-video-upload-session', JSON.stringify({
      fingerprint: 'resume.mp4:3:0', uploadId: 'u-2', expiresAt: Date.now() + 7_200_000,
    }))
    mocks.get.mockResolvedValueOnce({ code: 0, data: {
      uploadId: 'u-2', chunkSize: 2, totalParts: 2, status: 0,
      expiresAt: Date.now() + 7_200_000, uploadedParts: [{ partNumber: 1, sizeBytes: 2 }],
    } })
    mocks.post
      .mockResolvedValueOnce({ code: 0, data: {
        partNumber: 2, url: '/signed/u-2/2', expiresAt: Date.now() + 900_000,
      } })
      .mockResolvedValueOnce({ code: 0, data: {
        items: [{ mediaId: 704, mediaType: 'VIDEO', status: 11 }],
      } })
    const put = vi.fn().mockResolvedValue({ ok: true, status: 200 })
    vi.stubGlobal('fetch', put)

    await expect(mediaApi.uploadVideoResumable(file)).resolves.toMatchObject({ mediaId: 704 })

    expect(mocks.post.mock.calls.map(call => call[0])).toEqual([
      '/media/video-uploads/u-2/parts/2/url',
      '/media/video-uploads/u-2/complete',
    ])
    expect(put).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('scenary-video-upload-session')).toBeNull()
    vi.unstubAllGlobals()
  })
})
