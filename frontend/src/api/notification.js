import instance, { unwrap } from '@/utils/request'

export const notificationApi = {
  async list({ cursor, limit = 10 } = {}) {
    return unwrap(await instance.get('/notifications', {
      params: cursor == null ? { limit } : { cursor, limit },
    }))
  },
  async markRead(ids = []) {
    return unwrap(await instance.post('/notifications/read', { ids }))
  },
}
