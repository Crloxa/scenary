import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const state = vi.hoisted(() => ({
  notes: vi.fn(),
  like: vi.fn(),
  bookmark: vi.fn(),
  push: vi.fn(),
  route: { query: { q: '云海', sort: 'relevance' } },
}))

vi.mock('@/api/search', () => ({ searchApi: { notes: state.notes } }))
vi.mock('@/api/social', () => ({ socialApi: { like: state.like, bookmark: state.bookmark } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: true }) }))
vi.mock('vue-router', () => ({
  useRoute: () => state.route,
  useRouter: () => ({ push: state.push }),
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))

import SearchView from '@/views/SearchView.vue'

beforeEach(() => {
  state.notes.mockReset().mockResolvedValue({
    list: [{
      id: 9,
      title: '云海日出',
      contentPreview: '山顶的云海',
      coverUrl: '/cover.jpg',
      mediaCount: 1,
      author: { id: 7, nickname: '山客' },
      createdAt: 100,
      highlight: { title: '云海日出', content: null, placeName: null, author: null },
    }],
    nextCursor: null,
    hasMore: false,
  })
  state.like.mockReset()
  state.bookmark.mockReset()
  state.push.mockReset()
})

describe('SearchView', () => {
  it('loads route query and renders a plain-text highlight', async () => {
    const wrapper = mount(SearchView)
    await flushPromises()

    expect(state.notes).toHaveBeenCalledWith({ q: '云海', sort: 'relevance', cursor: undefined, limit: 10 })
    expect(wrapper.get('[data-testid="search-results"]').text()).toContain('云海日出')
    expect(wrapper.get('[data-testid="note-highlight"]').text()).toBe('云海日出')
  })

  it('pushes a shareable search route from the form', async () => {
    const wrapper = mount(SearchView)
    await flushPromises()

    await wrapper.get('#search-input').setValue('海边')
    await wrapper.get('button').trigger('click')

    expect(state.push).toHaveBeenCalledWith({
      path: '/search', query: { q: '海边', sort: 'relevance' },
    })
  })
})
