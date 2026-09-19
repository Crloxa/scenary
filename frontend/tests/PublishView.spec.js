import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  uploadOne: vi.fn(),
  uploadVideoResumable: vi.fn(),
  remove: vi.fn(),
  waitProcessed: vi.fn(),
  push: vi.fn(),
  create: vi.fn(),
  detail: vi.fn(),
  update: vi.fn(),
  routeParams: {},
  leaveGuard: null,
}))

vi.mock('@/api/media', () => ({
  mediaApi: {
    uploadOne: state.uploadOne,
    uploadVideoResumable: state.uploadVideoResumable,
    remove: state.remove,
  },
  waitProcessed: state.waitProcessed,
  isAbortError: error => error?.name === 'AbortError',
}))
vi.mock('@/api/note', () => ({ noteApi: { create: state.create, detail: state.detail, update: state.update } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: true }) }))
vi.mock('@/router', () => ({ default: { push: state.push } }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: state.push }),
  // P16-01 编辑模式读取路由参数；本套件聚焦发布模式，固定无 noteId
  useRoute: () => ({ params: state.routeParams }),
  onBeforeRouteLeave: guard => { state.leaveGuard = guard },
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))

import PublishView from '@/views/PublishView.vue'

beforeEach(() => {
  state.uploadOne.mockReset()
  state.uploadVideoResumable.mockReset()
  state.remove.mockReset().mockResolvedValue(undefined)
  state.waitProcessed.mockReset().mockResolvedValue({ status: 1 })
  state.push.mockReset()
  state.create.mockReset()
  state.detail.mockReset()
  state.update.mockReset()
  state.routeParams = {}
  state.leaveGuard = null
  if (!URL.createObjectURL) Object.defineProperty(URL, 'createObjectURL', { value: vi.fn() })
  if (!URL.revokeObjectURL) Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn() })
  URL.createObjectURL.mockReset().mockImplementation(file => `blob:${file.name}`)
  URL.revokeObjectURL.mockReset()
})

function files(count) {
  return Array.from({ length: count }, (_, i) =>
    new File(['image'], `photo-${i}.png`, { type: 'image/png' }))
}

function videoFile(name = 'clip.mp4') {
  return new File(['video'], name, { type: 'video/mp4' })
}

describe('PublishView upload queue', () => {
  it('protects unfinished drafts and blocks navigation while submitting', async () => {
    const wrapper = mount(PublishView)
    expect(state.leaveGuard()).toBe(true)

    await wrapper.get('[data-testid="input-title"]').setValue('未完成草稿')
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    expect(state.leaveGuard()).toBe(false)
    confirm.mockReturnValue(true)
    expect(state.leaveGuard()).toBe(true)

    state.uploadOne.mockResolvedValue({ mediaId: 101 })
    state.create.mockImplementation(() => new Promise(() => {}))
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: files(1) })
    await input.trigger('change')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(state.create).toHaveBeenCalled()
    expect(state.leaveGuard()).toBe(false)

    wrapper.unmount()
  })

  it('keeps at most three uploads active and releases previews on removal', async () => {
    const resolvers = []
    let nextId = 0
    state.uploadOne.mockImplementation(() => new Promise(resolve => resolvers.push(resolve)))
    const wrapper = mount(PublishView)
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: files(4) })

    await input.trigger('change')
    expect(state.uploadOne).toHaveBeenCalledTimes(3)

    resolvers.shift()({ mediaId: ++nextId })
    await flushPromises()
    expect(state.uploadOne).toHaveBeenCalledTimes(4)

    while (resolvers.length) resolvers.shift()({ mediaId: ++nextId })
    await flushPromises()
    expect(wrapper.findAll('[data-testid="upload-item"]')).toHaveLength(4)

    await wrapper.findAll('[aria-label="移除图片"]')[0].trigger('click')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:photo-0.png')
    expect(state.remove).toHaveBeenCalledWith(1)
    wrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(4)
  })

  it('uploads one video, waits for video readiness, and submits coordinates', async () => {
    state.uploadVideoResumable.mockResolvedValue({
      mediaId: 701, mediaType: 'VIDEO', status: 11,
    })
    state.waitProcessed.mockResolvedValue({
      mediaId: 701, mediaType: 'VIDEO', status: 12,
      playbackUrl: '/video-720.mp4', playbackLowUrl: '/video-480.mp4',
    })
    state.create.mockResolvedValue({ id: 88 })
    const wrapper = mount(PublishView)
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [videoFile()] })

    await input.trigger('change')
    await flushPromises()
    await wrapper.get('[data-testid="input-title"]').setValue('山谷短片')
    await wrapper.get('[data-testid="input-latitude"]').setValue('30.9785')
    await wrapper.get('[data-testid="input-longitude"]').setValue('102.7591')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(state.uploadVideoResumable).toHaveBeenCalledTimes(1)
    expect(state.uploadOne).not.toHaveBeenCalled()
    expect(state.waitProcessed).toHaveBeenCalledWith(701, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    expect(state.create).toHaveBeenCalledWith(expect.objectContaining({
      mediaIds: [701],
      latitude: 30.9785,
      longitude: 102.7591,
      placeSource: 'MAP',
      placePrecision: 'EXACT',
    }))
    expect(wrapper.find('video').exists()).toBe(true)
    wrapper.unmount()
  })

  it('edit mode prefills from detail and submits update without requestKey', async () => {
    state.routeParams = { noteId: '88' }
    state.detail.mockResolvedValue({
      id: 88, mine: true, title: '旧标题', content: '旧正文', placeName: '地点',
      latitude: 30.1, longitude: 102.2, placeSource: 'MAP', placePrecision: 'EXACT',
      visibility: 0,
      images: [
        { mediaId: 11, mediaType: 'IMAGE', thumbUrl: 'thumb-11.jpg' },
        { mediaId: 12, mediaType: 'IMAGE', thumbUrl: 'thumb-12.jpg' },
      ],
    })
    state.update.mockResolvedValue({ id: 88 })
    const wrapper = mount(PublishView)
    await flushPromises()

    expect(wrapper.get('[data-testid="input-title"]').element.value).toBe('旧标题')
    expect(wrapper.findAll('[data-testid="upload-item"]')).toHaveLength(2)
    await wrapper.get('[data-testid="input-title"]').setValue('新标题')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(state.update).toHaveBeenCalledTimes(1)
    expect(state.update).toHaveBeenCalledWith(88, expect.objectContaining({
      title: '新标题',
      mediaIds: [11, 12],
      visibility: 0,
    }))
    expect(state.update.mock.calls[0][1]).not.toHaveProperty('requestKey')
    expect(state.create).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('removing an existing media in edit mode does not delete the object', async () => {
    state.routeParams = { noteId: '88' }
    state.detail.mockResolvedValue({
      id: 88, mine: true, title: '旧标题', visibility: 1,
      images: [{ mediaId: 11, mediaType: 'IMAGE', thumbUrl: 'thumb-11.jpg' }],
    })
    const wrapper = mount(PublishView)
    await flushPromises()

    await wrapper.findAll('[aria-label="移除图片"]')[0].trigger('click')
    expect(state.remove).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
