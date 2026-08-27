/**
 * 登录态 store：双令牌持久化于 localStorage（强刷保持，P4 验收项）。
 * 原始 axios 调 refresh 以避免经过 request.js 拦截器形成自环。
 */
import { defineStore } from 'pinia'
import axios from 'axios'
import { toast } from '@/utils/toast'

const STORAGE_KEY = 'scenary-auth'

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {}
  } catch {
    return {}
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: load().accessToken || '',
    refreshToken: load().refreshToken || '',
    userId: load().userId || null,
    nickname: load().nickname || '',
    avatarUrl: load().avatarUrl || '',
  }),
  getters: {
    isLoggedIn: s => Boolean(s.accessToken && s.userId),
  },
  actions: {
    _persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          accessToken: this.accessToken,
          refreshToken: this.refreshToken,
          userId: this.userId,
          nickname: this.nickname,
          avatarUrl: this.avatarUrl,
        }),
      )
    },
    setAuth(data) {
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      this.userId = data.userId
      this.nickname = data.nickname
      this.avatarUrl = data.avatarUrl || ''
      this._persist()
    },
    patchProfile({ nickname, avatarUrl } = {}) {
      if (nickname !== undefined) this.nickname = nickname
      if (avatarUrl !== undefined) this.avatarUrl = avatarUrl
      this._persist()
    },
    /** 供 request.js 单飞刷新复用；成功旋转后本地令牌同步 */
    async doRefresh() {
      const { data } = await axios.post('/api/v1/auth/refresh', {
        refreshToken: this.refreshToken,
      })
      if (data.code !== 0) throw Object.assign(new Error(data.message), { code: data.code })
      this.setAuth(data.data)
      return data.data
    },
    forceLogout(silent = false) {
      this.accessToken = ''
      this.refreshToken = ''
      this.userId = null
      this.nickname = ''
      this.avatarUrl = ''
      localStorage.removeItem(STORAGE_KEY)
      if (!silent) toast('已退出登录')
    },
  },
})
