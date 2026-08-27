import instance, { unwrap } from '@/utils/request'

/** 客户端校验规则与 docs/02 §2.1 对齐 */
export const USERNAME_RE = /^[a-zA-Z0-9_]{4,20}$/

export function validateUsername(v) {
  if (!v) return '请输入用户名'
  if (!USERNAME_RE.test(v)) return '用户名需 4~20 位字母/数字/下划线'
  return ''
}

export function validatePassword(v) {
  if (!v) return '请输入密码'
  if (v.length < 8 || v.length > 64) return '密码长度 8~64'
  if (!(/[a-zA-Z]/.test(v) && /\d/.test(v))) return '密码需同时包含字母和数字'
  return ''
}

export const authApi = {
  async register(payload) {
    return unwrap(await instance.post('/auth/register', payload))
  },
  async login(payload) {
    return unwrap(await instance.post('/auth/login', payload))
  },
}
