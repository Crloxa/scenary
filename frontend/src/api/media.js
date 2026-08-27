import instance, { unwrap } from '@/utils/request'

/**
 * 批量上传：把已选文件分片成每请求 ≤1 张的独立调用，
 * 前端得以逐张呈现"处理中转圈/失败红标"并允许单独重传（docs/03 4.5）。
 * 服务端仍以 items 包络返回单元素数组。
 */
export const mediaApi = {
  async uploadOne(file) {
    const fd = new FormData()
    fd.append('files', file)
    const data = unwrap(await instance.post('/media/images', fd))
    return data.items[0]
  },
  async status(mediaId) {
    return unwrap(await instance.get(`/media/${mediaId}`))
  },
  async remove(mediaId) {
    return unwrap(await instance.delete(`/media/${mediaId}`))
  },
}

/** 轮询直至 status∈{1,2} 或超时（契约建议 800ms×30） */
export async function waitProcessed(mediaId, { intervalMs = 800, maxTries = 30 } = {}) {
  for (let i = 0; i < maxTries; i++) {
    const s = await mediaApi.status(mediaId)
    if (s.status === 1) return s
    if (s.status === 2) throw new Error('图片处理失败')
    await new Promise(r => setTimeout(r, intervalMs))
  }
  throw new Error('图片处理超时')
}
