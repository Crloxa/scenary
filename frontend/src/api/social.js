import instance, { unwrap } from '@/utils/request'

export const socialApi = {
  async like(noteId, enabled) {
    return unwrap(await instance.request({
      method: enabled ? 'put' : 'delete',
      url: `/notes/${noteId}/like`,
    }))
  },
  async bookmark(noteId, enabled) {
    return unwrap(await instance.request({
      method: enabled ? 'put' : 'delete',
      url: `/notes/${noteId}/bookmark`,
    }))
  },
  async follow(userId, enabled) {
    return unwrap(await instance.request({
      method: enabled ? 'put' : 'delete',
      url: `/users/${userId}/follow`,
    }))
  },
  async bookmarks({ cursor, limit = 10 } = {}) {
    return unwrap(await instance.get('/users/me/bookmarks', {
      params: cursor == null ? { limit } : { cursor, limit },
    }))
  },
}
