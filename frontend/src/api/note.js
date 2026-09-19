import instance, { unwrap } from '@/utils/request'

export const noteApi = {
  async create({
    title,
    content = '',
    placeName = '',
    latitude = null,
    longitude = null,
    placeSource = null,
    placePrecision = null,
    mediaIds,
    visibility = 1,
    requestKey = null,
  }) {
    return unwrap(
      await instance.post('/notes', {
        title,
        content,
        placeName,
        latitude,
        longitude,
        placeSource,
        placePrecision,
        mediaIds,
        visibility,
        requestKey,
      }),
    )
  },
  // P16-01 编辑笔记（docs/02 §5.11）：mediaIds 为全量替换语义
  async update(noteId, {
    title,
    content = '',
    placeName = '',
    latitude = null,
    longitude = null,
    placeSource = null,
    placePrecision = null,
    mediaIds,
    visibility = 1,
  }) {
    return unwrap(
      await instance.put(`/notes/${noteId}`, {
        title,
        content,
        placeName,
        latitude,
        longitude,
        placeSource,
        placePrecision,
        mediaIds,
        visibility,
      }),
    )
  },
  async detail(noteId) {
    return unwrap(await instance.get(`/notes/${noteId}`))
  },
  async remove(noteId) {
    return unwrap(await instance.delete(`/notes/${noteId}`))
  },
}
