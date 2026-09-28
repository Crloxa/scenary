import instance, { unwrap } from '@/utils/request'

export const placeApi = {
  async reverseGeocode({ latitude, longitude } = {}) {
    return unwrap(await instance.get('/places/reverse-geocode', {
      params: { latitude, longitude },
    }))
  },
  async mapNotes({ minLat, maxLat, minLng, maxLng, cursor, limit = 20 } = {}) {
    const params = { minLat, maxLat, minLng, maxLng, limit }
    if (cursor != null) params.cursor = cursor
    return unwrap(await instance.get('/places/notes', { params }))
  },
}

