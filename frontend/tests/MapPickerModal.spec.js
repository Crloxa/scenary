import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

// leaflet 全模块 mock：记录 map/tile/marker 的事件注册，供测试直接触发
const registry = vi.hoisted(() => ({
  mapHandlers: {}, tileHandlers: {}, markerHandlers: {},
  markerObj: null, mapObj: null, tileObj: null,
}))

vi.mock('leaflet', () => {
  const mapObj = {
    remove: vi.fn(),
    on: (type, handler) => { registry.mapHandlers[type] = handler },
  }
  const tileObj = {
    on: (type, handler) => { registry.tileHandlers[type] = handler },
    addTo: vi.fn(),
  }
  const markerObj = {
    on: (type, handler) => { registry.markerHandlers[type] = handler },
    setLatLng: vi.fn(),
    getLatLng: () => ({ lat: 12.345678, lng: 56.876543 }),
    addTo: vi.fn(),
  }
  registry.mapObj = mapObj
  registry.tileObj = tileObj
  registry.markerObj = markerObj
  return {
    default: {
      map: vi.fn(() => mapObj),
      tileLayer: vi.fn(() => tileObj),
      marker: vi.fn(() => markerObj),
      divIcon: vi.fn(options => options),
    },
  }
})

// 恒等坐标转换（TILE_IS_GCJ02=false）：选点读数/emit 断言不被 GCJ-02 往返偏移干扰
vi.mock('@/utils/mapTiles', () => ({
  TILE_URL: 'https://tile.test/{z}/{x}/{y}.png',
  TILE_SUBDOMAINS: '1',
  TILE_ATTRIBUTION: '© test',
  TILE_IS_GCJ02: false,
  toTileCoords: (lat, lng) => ({ lat, lng }),
  fromTileCoords: (lat, lng) => ({ lat, lng }),
}))

import MapPickerModal from '@/components/MapPickerModal.vue'

// onMounted 内惰性 import leaflet（异步挂载）：首次解析跨宏任务，
// mount 后必须 flush + 轮询等待地图初始化完成，再操作事件注册表
const mountModal = async () => {
  const wrapper = mount(MapPickerModal, { attachTo: document.body })
  await flushPromises()
  await vi.waitFor(() => {
    if (typeof registry.mapHandlers.click !== 'function') throw new Error('map not initialized yet')
  })
  return wrapper
}

describe('MapPickerModal（P12-E4 选点弹窗）', () => {
  it('挂载即初始化地图并展示初始读数', async () => {
    const wrapper = await mountModal()
    expect(wrapper.find('[data-testid="map-picker-canvas"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="map-picker-readout"]').text()).toContain('31')
    wrapper.unmount()
  })

  it('点击地图更新读数，确认后 emit pick（六位小数）', async () => {
    const wrapper = await mountModal()
    registry.mapHandlers.click({ latlng: { lat: 30.1234567, lng: 119.9876543 } })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="map-picker-readout"]').text()).toContain('30.123457')
    await wrapper.find('[data-testid="map-picker-confirm"]').trigger('click')
    expect(wrapper.emitted('pick')[0]).toEqual([{ latitude: 30.123457, longitude: 119.987654 }])
    wrapper.unmount()
  })

  it('marker 拖动结束同步读数', async () => {
    const wrapper = await mountModal()
    registry.markerHandlers.dragend()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="map-picker-readout"]').text()).toContain('12.345678')
    wrapper.unmount()
  })

  it('瓦片连续失败 ≥3 次显示降级提示（点选仍可用）', async () => {
    const wrapper = await mountModal()
    for (let i = 0; i < 3; i += 1) registry.tileHandlers.tileerror()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="map-tiles-fallback"]').exists()).toBe(true)
    registry.mapHandlers.click({ latlng: { lat: 1, lng: 2 } })
    await wrapper.find('[data-testid="map-picker-confirm"]').trigger('click')
    expect(wrapper.emitted('pick')[0]).toEqual([{ latitude: 1, longitude: 2 }])
    wrapper.unmount()
  })

  it('取消与关闭按钮 emit close，卸载时移除地图', async () => {
    const wrapper = await mountModal()
    await wrapper.find('[data-testid="map-picker-cancel"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
    expect(registry.mapObj.remove).toHaveBeenCalled()
  })
})
