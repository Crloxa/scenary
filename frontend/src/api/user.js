import instance, { unwrap } from '@/utils/request'

export const userApi = {
  async me() {
    return unwrap(await instance.get('/users/me'))
  },
  async updateProfile(payload) {
    return unwrap(await instance.patch('/users/me', payload))
  },
  /** 单文件上传，服务端居中裁切 200x200；返回 { avatarUrl } */
  async uploadAvatar(file) {
    const fd = new FormData()
    fd.append('file', file)
    return unwrap(await instance.post('/users/me/avatar', fd))
  },
  async profile(userId) {
    return unwrap(await instance.get(`/users/${userId}`))
  },
  /** showPrivate 由后端按令牌判定，前端无需传参 */
  async notes(userId, { cursor, limit = 12 } = {}) {
    return unwrap(
      await instance.get(`/users/${userId}/notes`, {
        params: cursor == null ? { limit } : { limit, cursor },
      }),
    )
  },
}
