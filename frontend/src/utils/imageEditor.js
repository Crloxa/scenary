// P17 修图与滤镜（docs/05 §13）：纯函数层——滤镜预设表、比例裁剪几何、旋转参数。
// canvas 绘制与导出在组件内完成，本模块不依赖 DOM，便于 Vitest 覆盖。

/** 滤镜预设：单选，值为 CSS filter 片段（同时用于预览与导出 canvas.filter） */
export const FILTERS = [
  { id: 'none', name: '原图', css: 'none' },
  { id: 'fresh', name: '清新', css: 'saturate(1.25) brightness(1.06) contrast(1.02)' },
  { id: 'air', name: '空气感', css: 'brightness(1.1) contrast(0.94) saturate(0.92)' },
  { id: 'film', name: '胶片', css: 'sepia(0.28) contrast(1.08) saturate(1.1) brightness(1.02)' },
  { id: 'sunset', name: '落日', css: 'sepia(0.4) saturate(1.4) hue-rotate(-12deg) brightness(1.03)' },
  { id: 'forest', name: '山野', css: 'saturate(1.3) hue-rotate(8deg) contrast(1.05)' },
  { id: 'mist', name: '雾色', css: 'saturate(0.7) brightness(1.08) contrast(0.92)' },
  { id: 'night', name: '夜幕', css: 'brightness(0.88) contrast(1.18) saturate(1.15)' },
  { id: 'mono', name: '黑白', css: 'grayscale(1) contrast(1.1)' },
  { id: 'silver', name: '银灰', css: 'grayscale(0.85) brightness(1.06) contrast(1.02)' },
  { id: 'cold', name: '冷调', css: 'hue-rotate(12deg) saturate(1.05) brightness(1.02)' },
  { id: 'warm', name: '暖调', css: 'hue-rotate(-8deg) saturate(1.15) brightness(1.04)' },
  { id: 'vivid', name: '浓烈', css: 'saturate(1.6) contrast(1.12)' },
  { id: 'soft', name: '柔光', css: 'brightness(1.08) contrast(0.9) saturate(0.95) blur(0.4px)' },
  { id: 'clarity', name: '通透', css: 'contrast(1.18) saturate(1.1) brightness(1.02)' },
  { id: 'dusk', name: '暮蓝', css: 'hue-rotate(18deg) saturate(0.9) brightness(0.95) contrast(1.08)' },
]

/** 裁剪比例预设（宽高比）；free 表示保留原图比例不裁剪 */
export const ASPECTS = [
  { id: 'free', label: '自由', ratio: null },
  { id: '1:1', label: '1:1', ratio: 1 },
  { id: '4:3', label: '4:3', ratio: 4 / 3 },
  { id: '3:2', label: '3:2', ratio: 3 / 2 },
  { id: '16:9', label: '16:9', ratio: 16 / 9 },
]

/**
 * 计算比例居中裁剪框（原图坐标）。
 * @returns {{x:number,y:number,w:number,h:number}} free 或无法裁剪时返回整图
 */
export function computeCrop(imgWidth, imgHeight, aspectId) {
  const aspect = ASPECTS.find(a => a.id === aspectId)
  const width = Math.max(1, Math.floor(imgWidth))
  const height = Math.max(1, Math.floor(imgHeight))
  if (!aspect || aspect.ratio == null) return { x: 0, y: 0, w: width, h: height }
  const target = aspect.ratio
  let w = width
  let h = Math.round(width / target)
  if (h > height) {
    h = height
    w = Math.round(height * target)
  }
  // 浮点取整可能溢出 1px，收敛到原图边界
  w = Math.min(w, width)
  h = Math.min(h, height)
  return {
    x: Math.floor((width - w) / 2),
    y: Math.floor((height - h) / 2),
    w,
    h,
  }
}

/** 旋转仅允许 0/90/180/270；非法值回落 0 */
export function normalizeRotation(rotation) {
  const value = Number(rotation) % 360
  return [0, 90, 180, 270].includes(value) ? value : 0
}

/**
 * 导出参数：给定原图尺寸与编辑状态，返回导出 canvas 的目标宽高（已含旋转互换）。
 */
export function exportSize(imgWidth, imgHeight, aspectId, rotation) {
  const crop = computeCrop(imgWidth, imgHeight, aspectId)
  const swap = normalizeRotation(rotation) === 90 || normalizeRotation(rotation) === 270
  const width = Math.min(crop.w, 4096)
  const height = Math.min(crop.h, 4096)
  return swap ? { width: height, height: width } : { width, height }
}

/** 编辑产物文件名：保留扩展名语义统一为 jpeg 导出 */
export function exportFileName(originalName = 'image.jpg') {
  const base = String(originalName).replace(/\.[^.]+$/, '') || 'image'
  return `${base}-edited.jpg`
}
