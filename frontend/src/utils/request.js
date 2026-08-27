import axios from 'axios'
import router from '@/router'
import { useUserStore } from '@/stores/user'

const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

instance.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.accessToken) {
    config.headers.Authorization = `Bearer ${store.accessToken}`
  }
  return config
})

/** 401 刷新单飞：并发请求共享一次 refresh，完成后重放各自原请求（docs/03 4.2） */
let refreshing = null

instance.interceptors.response.use(
  res => res.data,
  async error => {
    const { response, config } = error
    if (!response) {
      throw withText(error, '网络异常，请检查连接')
    }
    const env = response.data || {}
    const code = env.code

    // 令牌过期/失效 且 有刷新令牌：单飞刷新 + 重放一次
    if (
      response.status === 401 &&
      code === 40101 &&
      useUserStore().refreshToken &&
      !config._retried
    ) {
      config._retried = true
      refreshing ??= useUserStore()
        .doRefresh()
        .catch(() => null)
        .finally(() => {
          refreshing = null
        })
      const refreshed = await refreshing
      if (!refreshed) {
        kickToLogin()
      }
      return instance(config)
    }

    // 未登录访问受保护资源 / 刷新失败：清态回登录页并带回跳
    if (response.status === 401) {
      useUserStore().forceLogout(true)
      kickToLogin()
    }

    throw withText(error, env.message || fallback(response.status))
  },
)

function kickToLogin() {
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

function withText(error, text) {
  error.normalizedMessage = text
  return error
}

function fallback(status) {
  return { 400: '请求参数有误', 404: '内容不存在', 500: '服务开小差了，稍后再试' }[status] || `请求失败(${status})`
}

/** api/* 统一出口：拆包络，非 0 抛带 code 的错误（组件用 getErrorText 展示） */
export function unwrap(env) {
  if (env.code !== 0) {
    const e = new Error(env.message || '请求失败')
    e.code = env.code
    throw e
  }
  return env.data
}

export function getErrorText(e) {
  return e?.normalizedMessage || e?.message || '操作失败'
}

export default instance
