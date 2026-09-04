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
  async detail(noteId) {
    return unwrap(await instance.get(`/notes/${noteId}`))
  },
  async remove(noteId) {
    return unwrap(await instance.delete(`/notes/${noteId}`))
  },
}
