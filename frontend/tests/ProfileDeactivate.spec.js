import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  profile: vi.fn(),
  notes: vi.fn(),
  deactivateAccount: vi.fn(),
  forceLogout: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    profile: state.profile,
    notes: state.notes,
    deactivateAccount: state.deactivateAccount,
  },
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ isLoggedIn: true, userId: 7, forceLogout: state.forceLogout }),
}))
vi.mock('@/api/social', () => ({ socialApi: { follow: vi.fn() } }))
vi.mock('@/api/auth', () => ({ authApi: { logout: vi.fn() } }))
vi.mock('@/router', () => ({ default: { push: state.push } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' }, fullPath: '/user/7' }),
  useRouter: () => ({ push: state.push }),
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))

import ProfileView from '@/views/ProfileView.vue'

beforeEach(() => {
  for (const fn of Object.values(state)) fn.mockReset()
  state.profile.mockResolvedValue({
    id: 7,
    nickname: '山野行人',
    bio: '只拍山和海',
    avatarUrl: null,
    noteCount: 1,
    createdAt: 1700000000000,
  })
  state.notes.mockResolvedValue({ list: [], nextCursor: null, hasMore: false })
})

function mountSelf() {
  return mount(ProfileView)
}

describe('ProfileView 注销账号', () => {
  it('requires password before confirming and reports failure without logout', async () => {
    const wrapper = mountSelf()
    await flushPromises()

    await wrapper.get('[data-testid="btn-deactivate"]').trigger('click')
    const confirm = wrapper.get('[data-testid="btn-confirm-deactivate"]')
    expect(confirm.attributes('disabled')).toBeDefined()

    state.deactivateAccount.mockRejectedValue({ message: '密码确认失败' })
    await wrapper.get('[data-testid="input-deactivate-password"]').setValue('wrong-pass')
    await confirm.trigger('click')
    await flushPromises()

    expect(state.deactivateAccount).toHaveBeenCalledWith('wrong-pass')
    expect(state.forceLogout).not.toHaveBeenCalled()
    expect(state.push).not.toHaveBeenCalled()
  })

  it('deactivates, clears local session and returns home', async () => {
    const wrapper = mountSelf()
    await flushPromises()

    await wrapper.get('[data-testid="btn-deactivate"]').trigger('click')
    state.deactivateAccount.mockResolvedValue({ deactivated: true })
    await wrapper.get('[data-testid="input-deactivate-password"]').setValue('secret123')
    await wrapper.get('[data-testid="btn-confirm-deactivate"]').trigger('click')
    await flushPromises()

    expect(state.deactivateAccount).toHaveBeenCalledWith('secret123')
    expect(state.forceLogout).toHaveBeenCalled()
    expect(state.push).toHaveBeenCalledWith('/')
  })
})
