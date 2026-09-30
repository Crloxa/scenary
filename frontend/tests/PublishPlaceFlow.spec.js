import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
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
// P12-E4：mock leaflet 模块后直接使用真实 MapPickerModal（jsdom 渲染不了真地图）。
// 不 mock 组件模块本身——vitest 对 mock 命名空间的属性守卫会与 Vue 组件解析的
// 内部探针（__isTeleport/__v_isVNode/name…）无限互相抬杠
const mapRegistry = vi.hoisted(() => ({
  mapHandlers: {}, tileHandlers: {}, markerHandlers: {}, mapObj: null,
}))
vi.mock('leaflet', () => {
  const mapObj = {
    remove: vi.fn(),
    on: (type, handler) => { mapRegistry.mapHandlers[type] = handler },
  }
  const tileObj = { on: vi.fn(), addTo: vi.fn() }
  const markerObj = {
    on: (type, handler) => { mapRegistry.markerHandlers[type] = handler },
    setLatLng: vi.fn(), getLatLng: () => ({ lat: 1, lng: 2 }), addTo: vi.fn(),
  }
  mapRegistry.mapObj = mapObj
  return {
    default: {
      map: vi.fn(() => mapObj),
      tileLayer: vi.fn(() => tileObj),
      marker: vi.fn(() => markerObj),
      divIcon: vi.fn(options => options),
    },
  }
})

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

  it('地图选点确认 → 写入坐标(place_source=MAP) → 触发 E3 候选回填', async () => {
    state.reverseGeocode.mockResolvedValue({ placeName: '四姑娘山', provider: 'nominatim', cached: false })
    const wrapper = await mountView()
    await wrapper.find('[data-testid="btn-open-map-picker"]').trigger('click')
    // defineAsyncComponent 首次动态导入 + 解析跨多个宏任务（CI 慢机时长不定）→ 轮询等待
    await vi.waitFor(() => {
      if (!wrapper.find('[data-testid="map-picker-canvas"]').exists()) throw new Error('modal not ready')
    }, { timeout: 8000 })
    await wrapper.find('[data-testid="map-picker-confirm"]').trigger('click') // 默认初始坐标
    // 选点 → 弹窗关闭 + E3 防抖查询回填，全部轮询断言
    await vi.waitFor(() => {
      if (wrapper.find('[data-testid="map-picker-canvas"]').exists()) throw new Error('modal still open')
      if (wrapper.find('[data-testid="input-latitude"]').element.value !== '31.2304') throw new Error('lat not set')
    }, { timeout: 8000 })
    await vi.waitFor(() => {
      if (wrapper.find('#publish-place').element.value !== '四姑娘山') throw new Error('place not filled')
    }, { timeout: 8000 })
    wrapper.unmount()
  })
})
