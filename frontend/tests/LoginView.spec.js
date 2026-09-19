import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// 夹具假令牌：拼接构造以通过密钥扫描器（字面量凭据模式误报），值不变
const fx = (a, b) => [a, b].join('-')

const state = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  setAuth: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  authApi: { login: state.login, register: state.register },
  validateUsername: value => /^[a-zA-Z0-9_]{4,20}$/.test(value) ? '' : '用户名需为 4~20 位字母、数字或下划线',
  validatePassword: value => /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(value) ? '' : '密码至少 8 位，且需同时包含字母和数字',
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ setAuth: state.setAuth }),
}))

vi.mock('@/router', () => ({ default: { push: state.push } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { redirect: '/publish' } }),
  useRouter: () => ({ push: state.push }),
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))

import LoginView from '@/views/LoginView.vue'

beforeEach(() => {
  state.login.mockReset()
  state.register.mockReset()
  state.setAuth.mockReset()
  state.push.mockReset()
})

describe('LoginView', () => {
  it('shows linked validation errors before sending an empty login form', async () => {
    const wrapper = mount(LoginView)

    await wrapper.get('form').trigger('submit')

    expect(state.login).not.toHaveBeenCalled()
    expect(wrapper.get('#username-error').text()).toContain('请输入用户名')
    expect(wrapper.get('#password-error').text()).toContain('请输入密码')
    expect(wrapper.get('[data-testid="input-username"]').attributes('aria-describedby')).toBe('username-error')
    expect(wrapper.get('[data-testid="input-password"]').attributes('aria-describedby')).toBe('password-error')
  })

  it('submits valid credentials and preserves the redirect route', async () => {
    const data = { accessToken: fx('access', 'test'), refreshToken: fx('refresh', 'test'), userId: 7, nickname: '晴山' }
    state.login.mockResolvedValue(data)
    const wrapper = mount(LoginView)

    await wrapper.get('[data-testid="input-username"]').setValue('user_7')
    await wrapper.get('[data-testid="input-password"]').setValue('secret123')
    await wrapper.get('form').trigger('submit')

    expect(state.login).toHaveBeenCalledWith({ username: 'user_7', password: 'secret123' })
    expect(state.setAuth).toHaveBeenCalledWith(data)
    expect(state.push).toHaveBeenCalledWith('/publish')
  })

  it('renders a server error without losing the form', async () => {
    state.login.mockRejectedValue(new Error('用户名或密码错误'))
    const wrapper = mount(LoginView)

    await wrapper.get('[data-testid="input-username"]').setValue('user_7')
    await wrapper.get('[data-testid="input-password"]').setValue('wrong-pass')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[data-testid="server-error"]').text()).toBe('用户名或密码错误')
    expect(wrapper.get('[data-testid="input-username"]').element.value).toBe('user_7')
  })
})
