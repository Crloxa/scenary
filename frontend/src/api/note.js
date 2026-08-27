import instance, { unwrap } from '@/utils/request'

export const noteApi = {
  async create({ title, content = '', placeName = '', mediaIds, visibility = 1 }) {
    return unwrap(
      await instance.post('/notes', { title, content, placeName, mediaIds, visibility }),
    )
  },
  async detail(noteId) {
    return unwrap(await instance.get(`/notes/${noteId}`))
  },
  async remove(noteId) {
    return unwrap(await instance.delete(`/notes/${noteId}`))
  },
}
