import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// MapView 单测：mock leaflet 与 mapTiles（TILE_IS_GCJ02=false → 恒等转换，
// bbox 断言与 mock bounds 完全一致），聚焦"视野框查询/marker/空态/加载更多"编排。
const mocks = vi.hoisted(() => ({
  mapNotes: vi.fn(),
  mapHandlers: {},
  layerObj: null,
  markerCount: 0,
}))

vi.mock('@/api/place', () => ({ placeApi: { mapNotes: mocks.mapNotes } }))
vi.mock('@/utils/mapTiles', () => ({
  TILE_URL: 'https://tile.test/{z}/{x}/{y}.png',
  TILE_SUBDOMAINS: '1',
  TILE_ATTRIBUTION: '© test',
  TILE_IS_GCJ02: false,
  toTileCoords: (lat, lng) => ({ lat, lng }),
  fromTileCoords: (lat, lng) => ({ lat, lng }),
}))
vi.mock('leaflet', () => {
  const mapObj = {
    remove: vi.fn(),
    on: (type, handler) => { mocks.mapHandlers[type] = handler },
    getBounds: () => ({
      getSouth: () => 30.0, getWest: () => 102.0,
      getNorth: () => 31.0, getEast: () => 103.0,
    }),
  }
  const layerObj = {
    clearLayers: vi.fn(),
    addTo: vi.fn(),
  }
  Object.defineProperty(layerObj, 'markerCount', {
    get: () => mocks.markerCount,
  })
  mocks.layerObj = layerObj
  return {
    default: {
      map: vi.fn(() => mapObj),
      tileLayer: vi.fn(() => ({ on: vi.fn(), addTo: vi.fn() })),
      layerGroup: vi.fn(() => layerObj),
      marker: vi.fn(() => {
        mocks.markerCount += 1
        return { addTo: vi.fn(), on: vi.fn() }
      }),
      divIcon: vi.fn(options => options),
    },
  }
})
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))

import MapView from '@/views/MapView.vue'

const BBOX = { minLat: 30, maxLat: 31, minLng: 102, maxLng: 103 }

const mountView = async () => {
  const wrapper = mount(MapView)
  await flushPromises()
  // 惰性 import leaflet + 首次视野查询跨宏任务
  await vi.waitFor(() => {
    if (mocks.mapNotes.mock.calls.length === 0) throw new Error('not loaded yet')
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.markerCount = 0
  mocks.mapHandlers = {}
  mocks.mapNotes.mockResolvedValue({ list: [], nextCursor: null, hasMore: false })
})

describe('MapView（E5 地图浏览）', () => {
  it('挂载即以视野框查询公开笔记（默认 limit=20）', async () => {
    const wrapper = await mountView()
    expect(mocks.mapNotes).toHaveBeenCalledTimes(1)
    expect(mocks.mapNotes).toHaveBeenCalledWith({ ...BBOX, cursor: null, limit: 20 })
    wrapper.unmount()
  })

  it('返回笔记即渲染 marker，点击 marker 跳详情', async () => {
    mocks.mapNotes.mockResolvedValue({
      list: [
        { id: 901, title: '山与海', coverUrl: null, latitude: 30.5, longitude: 102.5, placeName: '四姑娘山', authorId: 7, authorNickname: '山客' },
        { id: 902, title: '湖泊', coverUrl: null, latitude: 30.6, longitude: 102.6, placeName: '', authorId: 8, authorNickname: '湖客' },
      ],
      nextCursor: 'abc',
      hasMore: true,
    })
    const wrapper = await mountView()
    // marker 渲染经 import('leaflet') 异步链，轮询等待而非同步断言
    await vi.waitFor(() => {
      expect(mocks.markerCount).toBe(2)
    })
    expect(wrapper.find('[data-testid="map-view-more"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="map-view-empty"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('空视野展示提示且不出现加载更多', async () => {
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="map-view-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="map-view-more"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('加载更多携带上一页 cursor 追加数据', async () => {
    mocks.mapNotes.mockResolvedValueOnce({
      list: [{ id: 901, title: '山与海', coverUrl: null, latitude: 30.5, longitude: 102.5, placeName: '', authorId: 7, authorNickname: '' }],
      nextCursor: 'CUR1',
      hasMore: true,
    })
    const wrapper = await mountView()
    await wrapper.find('[data-testid="map-view-more"]').trigger('click')
    await flushPromises()
    expect(mocks.mapNotes).toHaveBeenLastCalledWith({ ...BBOX, cursor: 'CUR1', limit: 20 })
    wrapper.unmount()
  })

  it('视野移动（moveend）后以新视野框重置查询', async () => {
    const wrapper = await mountView()
    mocks.mapNotes.mockClear()
    mocks.mapHandlers.moveend()
    await new Promise(resolve => setTimeout(resolve, 600)) // moveend 防抖 500ms
    await flushPromises()
    expect(mocks.mapNotes).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
