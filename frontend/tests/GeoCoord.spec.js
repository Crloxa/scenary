import { describe, expect, it } from 'vitest'
import { wgs84ToGcj02, gcj02ToWgs84 } from '@/utils/geoCoord'

describe('geoCoord（E5 WGS84↔GCJ-02）', () => {
  it('国内坐标存在合法量级的偏移（北京样本）', () => {
    const { lat, lng } = wgs84ToGcj02(39.915, 116.404)
    expect(lat).not.toBe(39.915)
    expect(lng).not.toBe(116.404)
    // GCJ-02 偏移通常在数十米~数百米量级（<0.02°），过小或过大都说明算法错误
    expect(Math.abs(lat - 39.915)).toBeGreaterThan(0.0005)
    expect(Math.abs(lat - 39.915)).toBeLessThan(0.02)
    expect(Math.abs(lng - 116.404)).toBeGreaterThan(0.0005)
    expect(Math.abs(lng - 116.404)).toBeLessThan(0.02)
  })

  it('往返转换闭合到 1e-5 度以内（国内多点采样）', () => {
    const samples = [
      [30.9785, 102.7591], // 四姑娘山
      [39.915, 116.404], // 北京
      [31.2304, 121.4737], // 上海
      [22.5, 114.05], // 深圳
    ]
    samples.forEach(([lat, lng]) => {
      const gcj = wgs84ToGcj02(lat, lng)
      const back = gcj02ToWgs84(gcj.lat, gcj.lng)
      expect(Math.abs(back.lat - lat)).toBeLessThan(1e-5)
      expect(Math.abs(back.lng - lng)).toBeLessThan(1e-5)
    })
  })

  it('境外坐标原样返回（不做偏移）', () => {
    expect(wgs84ToGcj02(35.0, -78.0)).toEqual({ lat: 35.0, lng: -78.0 })
    expect(gcj02ToWgs84(35.0, -78.0)).toEqual({ lat: 35.0, lng: -78.0 })
  })
})
