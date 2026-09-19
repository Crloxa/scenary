import instance, { unwrap } from '@/utils/request'

export const placeApi = {
  async reverseGeocode({ latitude, longitude } = {}) {
    return unwrap(await instance.get('/places/reverse-geocode', {
      params: { latitude, longitude },
    }))
  },
}
