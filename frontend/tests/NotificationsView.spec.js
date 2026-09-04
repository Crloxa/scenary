import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  markRead: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/notification', () => ({
  notificationApi: { list: mocks.list, markRead: mocks.markRead },
}))
vi.mock('@/utils/request', () => ({ getErrorText: error => error?.message || '操作失败' }))
vi.mock('@/utils/toast', () => ({ toast: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))

import NotificationsView from '@/views/NotificationsView.vue'

beforeEach(() => {
  mocks.list.mockReset().mockResolvedValue({
    list: [{
      id: 801,
      type: 'COMMENT',
      actor: { id: 7, nickname: '山间来客' },
      noteId: 9,
      noteTitle: '云海',
      commentPreview: '好看的云',
      readAt: null,
      createdAt: 100,
    }],
    nextCursor: null,
    hasMore: false,
    unreadCount: 1,
  })
  mocks.markRead.mockReset().mockResolvedValue(undefined)
  mocks.push.mockReset()
})

describe('NotificationsView', () => {
  it('renders unread notifications and opens the related note as read', async () => {
    const wrapper = mount(NotificationsView)
    await flushPromises()

    expect(wrapper.find('[data-testid="notification-item"]').text()).toContain('评论了你的笔记')
    expect(wrapper.text()).toContain('全部已读（1）')

    await wrapper.get('[data-testid="notification-item"]').trigger('click')

    expect(mocks.markRead).toHaveBeenCalledWith([801])
    expect(mocks.push).toHaveBeenCalledWith('/note/9')
  })

  it('marks every unread notification as read', async () => {
    const wrapper = mount(NotificationsView)
    await flushPromises()

    await wrapper.get('button').trigger('click')

    expect(mocks.markRead).toHaveBeenCalledWith([])
    expect(wrapper.text()).toContain('全部已读')
  })
})
