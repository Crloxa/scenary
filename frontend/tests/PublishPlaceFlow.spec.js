import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  uploadOne: vi.fn(), uploadVideoResumable: vi.fn(), remove: vi.fn(),
  waitProcessed: vi.fn(), push: vi.fn(), create: vi.fn(), detail: vi.fn(),
  update: vi.fn(), routeParams: {}, leaveGuard: null,
  reverseGeocode: vi.fn(),
}))

vi.mock('@/api/media', () => ({
  mediaApi: { uploadOne: state.uploadOne, uploadVideoResumable: state.uploadVideoResumable, remove: state.remove },
  waitProcessed: state.waitProcessed,
  isAbortError: error => error?.name === 'AbortError',
}))
vi.mock('@/api/note', () => ({ noteApi: { create: state.create, detail: state.detail, update: state.update } }))
vi.mock('@/api/place', () => ({ placeApi: { reverseGeocode: state.reverseGeocode } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: true }) }))
vi.mock('@/router', () => ({ default: { push: state.push } }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: state.push }),
  useRoute: () => ({ params: state.routeParams }),
  onBeforeRouteLeave: guard => { state.leaveGuard = guard },
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))

import PublishView from '@/views/PublishView.vue'

const mountView = async () => {
  const wrapper = mount(PublishView, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

const setCoordinates = async (wrapper, lat, lng) => {
  await wrapper.find('[data-testid="input-latitude"]').setValue(lat)
  await wrapper.find('[data-testid="input-longitude"]').setValue(lng)
  // E3 候选地名查询防抖 600ms（docs/05 §6.5 E3-03）
  await new Promise(resolve => setTimeout(resolve, 650))
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  state.routeParams = {}
})

describe('PublishView × P12-E3 逆地理联动', () => {
  it('坐标齐备且地名为空时自动回填候选（仍可编辑）', async () => {
    state.reverseGeocode.mockResolvedValue({ placeName: '外滩', provider: 'nominatim', cached: false })
    const wrapper = await mountView()
    await setCoordinates(wrapper, '31.2304', '121.4737')
    expect(state.reverseGeocode).toHaveBeenCalledWith({ latitude: 31.2304, longitude: 121.4737 })
    expect(wrapper.find('#publish-place').element.value).toBe('外滩')
    // 空字段回填不出现候选条
    expect(wrapper.find('[data-testid="place-suggestion"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('地名已填时不静默覆盖，展示候选条并由用户确认', async () => {
    state.reverseGeocode.mockResolvedValue({ placeName: '外滩', provider: 'nominatim', cached: true })
    const wrapper = await mountView()
    await wrapper.find('#publish-place').setValue('我的自定义地点')
    await setCoordinates(wrapper, '31.2304', '121.4737')
    expect(wrapper.find('#publish-place').element.value).toBe('我的自定义地点')
    const chip = wrapper.find('[data-testid="place-suggestion"]')
    expect(chip.exists()).toBe(true)
    expect(chip.text()).toContain('外滩')
    await wrapper.find('[data-testid="apply-place-suggestion"]').trigger('click')
    expect(wrapper.find('#publish-place').element.value).toBe('外滩')
    expect(wrapper.find('[data-testid="place-suggestion"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('空候选/接口失败静默降级：不回填、不出候选条、不报错', async () => {
    state.reverseGeocode.mockRejectedValue(Object.assign(new Error('地名查询过于频繁'), { code: 42001 }))
    const wrapper = await mountView()
    await setCoordinates(wrapper, '31.2304', '121.4737')
    expect(wrapper.find('#publish-place').element.value).toBe('')
    expect(wrapper.find('[data-testid="place-suggestion"]').exists()).toBe(false)
    const { toast } = await import('@/utils/toast')
    expect(toast).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('坐标不完整或非法时不触发查询', async () => {
    const wrapper = await mountView()
    await wrapper.find('[data-testid="input-latitude"]').setValue('91')
    await new Promise(resolve => setTimeout(resolve, 650))
    await flushPromises()
    expect(state.reverseGeocode).not.toHaveBeenCalled()
    await wrapper.find('[data-testid="input-latitude"]').setValue('')
    await new Promise(resolve => setTimeout(resolve, 650))
    await flushPromises()
    expect(state.reverseGeocode).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
