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
  /** 注销账号（docs/02 §3.8）：密码二次确认，成功后令牌即时吊销 */
  async deactivateAccount(password) {
    return unwrap(await instance.delete('/users/me', { data: { password } }))
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
