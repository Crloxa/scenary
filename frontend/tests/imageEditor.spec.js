import { describe, expect, it } from 'vitest'
import {
  FILTERS, ASPECTS, computeCrop, normalizeRotation, exportSize, exportFileName,
} from '@/utils/imageEditor'

describe('imageEditor 纯函数', () => {
  it('滤镜预设结构完整且 css 可直接用于 canvas.filter', () => {
    expect(FILTERS.length).toBeGreaterThanOrEqual(15)
    const ids = new Set(FILTERS.map(f => f.id))
    expect(ids.size).toBe(FILTERS.length)
    expect(ids.has('none')).toBe(true)
    for (const f of FILTERS) {
      expect(typeof f.name).toBe('string')
      expect(f.css.length).toBeGreaterThan(0)
      expect(f.css).not.toMatch(/url\(|javascript:/i)
    }
  })

  it('free 比例不裁剪，返回整图', () => {
    expect(computeCrop(1080, 1440, 'free')).toEqual({ x: 0, y: 0, w: 1080, h: 1440 })
    expect(computeCrop(1080, 1440, '不存在')).toEqual({ x: 0, y: 0, w: 1080, h: 1440 })
  })

  it('横图按 1:1 居中裁剪，竖图按 16:9 上下裁剪', () => {
    expect(computeCrop(1200, 900, '1:1')).toEqual({ x: 150, y: 0, w: 900, h: 900 })
    const crop = computeCrop(1080, 1440, '16:9')
    expect(crop.w).toBe(1080)
    expect(crop.h).toBe(608)
    expect(crop.x).toBe(0)
    expect(crop.y).toBe(Math.floor((1440 - 608) / 2))
  })

  it('极小图不产生越界裁剪', () => {
    const crop = computeCrop(3, 3, '16:9')
    expect(crop.w).toBeLessThanOrEqual(3)
    expect(crop.h).toBeLessThanOrEqual(3)
    expect(crop.x).toBeGreaterThanOrEqual(0)
    expect(crop.y).toBeGreaterThanOrEqual(0)
  })

  it('旋转归一化：非法值回落 0，270+90 回到 0', () => {
    expect(normalizeRotation(90)).toBe(90)
    expect(normalizeRotation(450)).toBe(90)
    expect(normalizeRotation(-90)).toBe(0)
    expect(normalizeRotation('x')).toBe(0)
  })

  it('导出尺寸含旋转互换且长边收敛 4096', () => {
    expect(exportSize(1200, 900, '1:1', 0)).toEqual({ width: 900, height: 900 })
    expect(exportSize(1200, 900, '1:1', 90)).toEqual({ width: 900, height: 900 })
    const big = exportSize(6000, 3000, 'free', 0)
    expect(big.width).toBe(4096)
    expect(big.height).toBe(3000)
    const swapped = exportSize(6000, 3000, 'free', 90)
    expect(swapped).toEqual({ width: 3000, height: 4096 })
  })

  it('导出文件名统一 jpeg 后缀', () => {
    expect(exportFileName('IMG_001.png')).toBe('IMG_001-edited.jpg')
    expect(exportFileName()).toBe('image-edited.jpg')
    expect(ASPECTS.map(a => a.id)).toContain('1:1')
  })
})
