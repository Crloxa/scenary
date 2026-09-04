import instance, { unwrap } from '@/utils/request'

const VIDEO_UPLOAD_STORAGE_KEY = 'scenary-video-upload-session'

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
  async createVideoUpload(payload, config = {}) {
    return unwrap(await instance.post('/media/video-uploads', payload, config))
  },
  async videoUploadSession(uploadId, config = {}) {
    return unwrap(await instance.get(`/media/video-uploads/${uploadId}`, config))
  },
  async videoUploadPartUrl(uploadId, partNumber, config = {}) {
    return unwrap(await instance.post(
      `/media/video-uploads/${uploadId}/parts/${partNumber}/url`, null, config))
  },
  async completeVideoUpload(uploadId, config = {}) {
    const data = unwrap(await instance.post(`/media/video-uploads/${uploadId}/complete`, null, config))
    return data.items[0]
  },
  async cancelVideoUpload(uploadId, config = {}) {
    return unwrap(await instance.delete(`/media/video-uploads/${uploadId}`, config))
  },
  async status(mediaId, config = {}) {
    return unwrap(await instance.get(`/media/${mediaId}`, config))
  },
  async remove(mediaId, config = {}) {
    return unwrap(await instance.delete(`/media/${mediaId}`, config))
  },
}

/**
 * 视频 E1 上传：会话元数据存 localStorage，分片使用预签名 URL 直传 MinIO。
 * 浏览器刷新后需重新选择同一文件，已存在且大小正确的分片会被跳过。
 */
mediaApi.uploadVideoResumable = async function uploadVideoResumable(file, {
  signal,
  onProgress = () => {},
} = {}) {
  const fingerprint = [file?.name || '', file?.size || 0, file?.lastModified || 0].join(':')
  let stored = readStoredSession()
  let session = null

  if (stored?.fingerprint === fingerprint && stored.expiresAt > Date.now()) {
    try {
      session = await mediaApi.videoUploadSession(stored.uploadId, { signal })
    } catch (error) {
      if (!isResumableSessionGone(error)) throw error
      clearStoredSession()
      stored = null
    }
  }

  if (!session) {
    session = await mediaApi.createVideoUpload({
      fileName: file.name,
      sizeBytes: file.size,
      mime: file.type || null,
    }, { signal })
    stored = { fingerprint, uploadId: session.uploadId, expiresAt: session.expiresAt }
    saveStoredSession(stored)
  }

  if (session.status === 3) {
    clearStoredSession()
    throw uploadError('上传会话已过期，请重新选择视频', 40902)
  }
  if (session.status === 2) {
    const completed = await mediaApi.completeVideoUpload(session.uploadId, { signal })
    clearStoredSession()
    return completed
  }

  const uploaded = new Map((session.uploadedParts || []).map(part => [part.partNumber, part.sizeBytes]))
  let uploadedBytes = [...uploaded.entries()].reduce((sum, [partNumber, size]) => {
    const expected = expectedPartSize(file.size, session.chunkSize, session.totalParts, partNumber)
    return sum + (size === expected ? size : 0)
  }, 0)
  onProgress(uploadedBytes, file.size)

  for (let partNumber = 1; partNumber <= session.totalParts; partNumber++) {
    throwIfAborted(signal)
    const expected = expectedPartSize(file.size, session.chunkSize, session.totalParts, partNumber)
    if (uploaded.get(partNumber) === expected) continue

    const start = (partNumber - 1) * session.chunkSize
    const blob = file.slice(start, start + expected)
    const signed = await mediaApi.videoUploadPartUrl(session.uploadId, partNumber, { signal })
    const response = await fetch(signed.url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: blob,
      signal,
    })
    if (!response.ok) {
      throw uploadError(`第 ${partNumber} 个分片上传失败`, response.status)
    }
    uploaded.set(partNumber, expected)
    uploadedBytes += expected
    stored = { ...stored, expiresAt: session.expiresAt, uploadedParts: [...uploaded.entries()] }
    saveStoredSession(stored)
    onProgress(uploadedBytes, file.size)
  }

  const completed = await mediaApi.completeVideoUpload(session.uploadId, { signal })
  clearStoredSession()
  return completed
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

function expectedPartSize(fileSize, chunkSize, totalParts, partNumber) {
  return partNumber === totalParts
    ? fileSize - (totalParts - 1) * chunkSize
    : chunkSize
}

function throwIfAborted(signal) {
  if (signal?.aborted) throw abortError()
}

function uploadError(message, code) {
  const error = new Error(message)
  error.code = code
  return error
}

function readStoredSession() {
  try {
    const value = JSON.parse(localStorage.getItem(VIDEO_UPLOAD_STORAGE_KEY))
    return value && typeof value === 'object' ? value : null
  } catch {
    return null
  }
}

function saveStoredSession(value) {
  try {
    localStorage.setItem(VIDEO_UPLOAD_STORAGE_KEY, JSON.stringify(value))
  } catch {
    // 存储被禁用时仍允许当前页完成上传，只失去刷新恢复能力。
  }
}

function clearStoredSession() {
  try {
    localStorage.removeItem(VIDEO_UPLOAD_STORAGE_KEY)
  } catch {
    // 同上，不影响当前上传结果。
  }
}

function isResumableSessionGone(error) {
  const code = error?.code || error?.response?.data?.code
  return code === 40400 || code === 40902 || code === 40300
    || error?.response?.status === 404
}

export function isAbortError(error) {
  return error?.name === 'AbortError' || error?.code === 'ERR_CANCELED'
}
