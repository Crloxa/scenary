import instance, { unwrap } from '@/utils/request'

/**
 * 批量上传：把已选文件分片成每请求 ≤1 张的独立调用，
 * 前端得以逐张呈现"处理中转圈/失败红标"并允许单独重传（docs/03 4.5）。
 * 服务端仍以 items 包络返回单元素数组。
 */
export const mediaApi = {
  async uploadOne(file, config = {}) {
    const fd = new FormData()
    fd.append('files', file)
    const data = unwrap(await instance.post('/media/images', fd, config))
    return data.items[0]
  },
  async uploadVideo(file, config = {}) {
    const fd = new FormData()
    fd.append('file', file)
    const data = unwrap(await instance.post('/media/videos', fd, config))
    return data.items[0]
  },
  async status(mediaId, config = {}) {
    return unwrap(await instance.get(`/media/${mediaId}`, config))
  },
  async remove(mediaId, config = {}) {
    return unwrap(await instance.delete(`/media/${mediaId}`, config))
  },
}

/** 轮询直至图片/视频完成或失败（契约建议 800ms×30）。 */
export async function waitProcessed(mediaId, { intervalMs = 800, maxTries = 30, signal } = {}) {
  for (let i = 0; i < maxTries; i++) {
    if (signal?.aborted) throw abortError()
    const s = await mediaApi.status(mediaId, { signal })
    if (s.status === 1 || s.status === 12) return s
    if (s.status === 2 || s.status === 13 || s.status === 14) throw new Error('媒体处理失败')
    await waitWithAbort(intervalMs, signal)
  }
  throw new Error('媒体处理超时')
}

function waitWithAbort(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(abortError())
    const timer = setTimeout(done, ms)
    function done() {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }
    function onAbort() {
      clearTimeout(timer)
      signal?.removeEventListener('abort', onAbort)
      reject(abortError())
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

function abortError() {
  const error = new Error('请求已取消')
  error.name = 'AbortError'
  return error
}

export function isAbortError(error) {
  return error?.name === 'AbortError' || error?.code === 'ERR_CANCELED'
}
