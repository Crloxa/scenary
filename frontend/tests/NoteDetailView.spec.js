import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  detail: vi.fn(),
  list: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/note', () => ({ noteApi: { detail: mocks.detail } }))
vi.mock('@/api/social', () => ({ socialApi: { like: vi.fn(), bookmark: vi.fn() } }))
vi.mock('@/api/comment', () => ({ commentApi: { list: mocks.list, create: vi.fn(), remove: vi.fn() } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: false }) }))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '88' }, fullPath: '/note/88' }),
  useRouter: () => ({ push: mocks.push }),
}))

import NoteDetailView from '@/views/NoteDetailView.vue'

beforeEach(() => {
  mocks.detail.mockReset().mockResolvedValue({
    id: 88,
    title: '山谷短片',
    content: '云在山腰。',
    placeName: '四姑娘山',
    placeSource: 'MAP',
    latitude: 30.9785,
    longitude: 102.7591,
    visibility: 1,
    createdAt: 100,
    author: { id: 7, nickname: '山客', avatarUrl: null },
    images: [{
      mediaId: 701,
      mediaType: 'VIDEO',
      url: '/cover.jpg',
      thumbUrl: '/cover.jpg',
      playbackUrl: '/video-720.mp4',
      playbackLowUrl: '/video-480.mp4',
    }],
    social: {},
    mine: false,
  })
  mocks.list.mockReset().mockResolvedValue({ list: [], nextCursor: null, hasMore: false })
  mocks.push.mockReset()
})

describe('NoteDetailView P12 media rendering', () => {
  it('renders a video player with poster and transcoded playback URL', async () => {
    const wrapper = mount(NoteDetailView)
    await flushPromises()

    const video = wrapper.get('video')
    expect(video.attributes('src')).toBe('/video-720.mp4')
    expect(video.attributes('poster')).toBe('/cover.jpg')
    expect(video.attributes('controls')).toBe('')
    expect(wrapper.find('img[alt*="图"]').exists()).toBe(false)
  })
})
