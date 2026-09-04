import instance, { unwrap } from '@/utils/request'

export const commentApi = {
  async list(noteId, { cursor, limit = 10 } = {}) {
    return unwrap(await instance.get(`/notes/${noteId}/comments`, {
      params: cursor == null ? { limit } : { cursor, limit },
    }))
  },
  async create(noteId, { content, parentId = null }) {
    return unwrap(await instance.post(`/notes/${noteId}/comments`, { content, parentId }))
  },
  async remove(commentId) {
    return unwrap(await instance.delete(`/comments/${commentId}`))
  },
}
