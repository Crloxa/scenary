import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  requestHandler: null,
  responseErrorHandler: null,
  instance: null,
  push: vi.fn(),
  store: {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    userId: 7,
    nickname: '晴山',
    avatarUrl: '',
    doRefresh: vi.fn(),
    forceLogout: vi.fn(),
  },
}))

vi.mock('axios', () => {
  const instance = vi.fn(config => Promise.resolve({ data: { code: 0, data: config.url } }))
  instance.interceptors = {
    request: { use: vi.fn(fn => { state.requestHandler = fn }) },
    response: { use: vi.fn((ok, err) => { state.responseErrorHandler = err }) },
  }
  state.instance = instance
  const create = vi.fn(() => instance)
  const isCancel = vi.fn(error => Boolean(error?.__cancel))
  return { default: { create, isCancel }, create, isCancel }
})

vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { path: '/home', fullPath: '/home?from=test' } },
    push: state.push,
  },
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => state.store,
}))

import '@/utils/request'

beforeEach(() => {
  state.push.mockReset()
  state.store.accessToken = 'access-1'
  state.store.refreshToken = 'refresh-1'
  state.store.userId = 7
  state.store.doRefresh.mockReset()
  state.store.forceLogout.mockReset()
})

describe('request interceptor', () => {
  it('adds the current bearer token', () => {
    const next = state.requestHandler({ headers: {} })
    expect(next.headers.Authorization).toBe('Bearer access-1')
  })

  it('shares one refresh across concurrent expired requests', async () => {
    let resolveRefresh
    state.store.doRefresh.mockReturnValue(new Promise(resolve => { resolveRefresh = resolve }))
    state.instance.mockImplementation(config => Promise.resolve({ ok: config.url }))

    const errors = ['/a', '/b', '/c', '/d', '/e'].map(url => ({
      response: { status: 401, data: { code: 40101, message: 'expired' } },
      config: { url },
    }))
    const pending = errors.map(error => state.responseErrorHandler(error))

    expect(state.store.doRefresh).toHaveBeenCalledTimes(1)
    resolveRefresh({ accessToken: 'access-2', refreshToken: 'refresh-2', userId: 7, nickname: '晴山', avatarUrl: '' })

    await expect(Promise.all(pending)).resolves.toEqual(['/a', '/b', '/c', '/d', '/e'].map(url => ({ ok: url })))
    expect(state.instance).toHaveBeenCalledTimes(5)
    expect(state.store.forceLogout).not.toHaveBeenCalled()
  })

  it('does not replay the original request after refresh failure', async () => {
    state.store.doRefresh.mockRejectedValue(new Error('refresh failed'))
    const error = { response: { status: 401, data: { code: 40101, message: 'expired' } }, config: { url: '/a' } }

    await expect(state.responseErrorHandler(error)).rejects.toMatchObject({ normalizedMessage: '登录已过期，请重新登录' })
    expect(state.store.forceLogout).toHaveBeenCalledWith(true)
    expect(state.push).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/home?from=test' } })
    expect(state.instance).not.toHaveBeenCalled()
  })
})
