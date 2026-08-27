import instance, { unwrap } from '@/utils/request'

export const feedApi = {
  /** 首页不传 cursor（L1 缓存路径），翻页带上一页 nextCursor */
  async list({ cursor, limit = 10 } = {}) {
    return unwrap(
      await instance.get('/feed', { params: cursor == null ? { limit } : { limit, cursor } }),
    )
  },
}
