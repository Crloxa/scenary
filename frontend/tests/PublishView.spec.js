import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  uploadOne: vi.fn(),
  remove: vi.fn(),
  waitProcessed: vi.fn(),
  push: vi.fn(),
  create: vi.fn(),
  leaveGuard: null,
}))

vi.mock('@/api/media', () => ({
  mediaApi: { uploadOne: state.uploadOne, remove: state.remove },
  waitProcessed: state.waitProcessed,
  isAbortError: error => error?.name === 'AbortError',
}))
vi.mock('@/api/note', () => ({ noteApi: { create: state.create } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: true }) }))
vi.mock('@/router', () => ({ default: { push: state.push } }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: state.push }),
  onBeforeRouteLeave: guard => { state.leaveGuard = guard },
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))

import PublishView from '@/views/PublishView.vue'

beforeEach(() => {
  state.uploadOne.mockReset()
  state.remove.mockReset().mockResolvedValue(undefined)
  state.waitProcessed.mockReset().mockResolvedValue({ status: 1 })
  state.push.mockReset()
  state.create.mockReset()
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
})
