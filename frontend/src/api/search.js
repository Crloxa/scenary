import instance, { unwrap } from '@/utils/request'

export const searchApi = {
  async notes({ q, cursor, limit = 10, sort = 'recent' } = {}) {
    const params = { q, limit, sort }
    if (cursor != null) params.cursor = cursor
    return unwrap(await instance.get('/search/notes', { params }))
  },
}
