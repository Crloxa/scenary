import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const registry = vi.hoisted(() => ({
  tileHandlers: {}, mapObj: null,
}))

vi.mock('leaflet', () => {
  const mapObj = { remove: vi.fn(), on: vi.fn() }
  const tileObj = {
    on: (type, handler) => { registry.tileHandlers[type] = handler },
    addTo: vi.fn(),
  }
  const markerObj = { addTo: vi.fn() }
  registry.mapObj = mapObj
  return {
    default: {
      map: vi.fn(() => mapObj),
      tileLayer: vi.fn(() => tileObj),
      marker: vi.fn(() => markerObj),
      divIcon: vi.fn(options => options),
    },
  }
})

import MiniMap from '@/components/MiniMap.vue'

describe('MiniMap（P12-E4 详情小地图）', () => {
  it('渲染地图容器与坐标文本（含地名）', () => {
    const wrapper = mount(MiniMap, {
      props: { latitude: 30.9785, longitude: 102.7591, placeName: '四姑娘山' },
    })
    expect(wrapper.find('[data-testid="mini-map-canvas"]').exists()).toBe(true)
    const caption = wrapper.find('[data-testid="mini-map-coords"]').text()
    expect(caption).toContain('四姑娘山')
    expect(caption).toContain('30.9785')
    expect(caption).toContain('102.7591')
    wrapper.unmount()
  })

  it('瓦片连续失败 ≥3 次降级为纯坐标文本', async () => {
    const wrapper = mount(MiniMap, {
      props: { latitude: 1.5, longitude: 2.5, placeName: '' },
    })
    for (let i = 0; i < 3; i += 1) registry.tileHandlers.tileerror()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="mini-map-canvas"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="mini-map-fallback"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mini-map-coords"]').text()).toContain('1.5')
    wrapper.unmount()
  })

  it('卸载时移除地图实例', () => {
    const wrapper = mount(MiniMap, { props: { latitude: 1, longitude: 2 } })
    wrapper.unmount()
    expect(registry.mapObj.remove).toHaveBeenCalled()
  })
})
