import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import NoteCard from '@/components/NoteCard.vue'

describe('NoteCard', () => {
  it('emits note and author navigation actions', async () => {
    const wrapper = mount(NoteCard, {
      props: {
        card: {
          id: 7,
          title: '晨雾',
          coverUrl: '/cover.jpg',
          mediaCount: 3,
          author: { id: 11, nickname: '晴山' },
        },
      },
    })

    await wrapper.trigger('click')
    expect(wrapper.emitted('open')).toEqual([[7]])

    const author = wrapper.findAll('span').find(node => node.text() === '晴山')
    expect(author).toBeTruthy()
    await author.trigger('click')
    expect(wrapper.emitted('open-user')).toEqual([[11]])
  })

  it('emits an idempotent social action intent without opening the card', async () => {
    const wrapper = mount(NoteCard, {
      props: {
        card: {
          id: 8,
          title: '海边',
          coverUrl: '/cover.jpg',
          mediaCount: 1,
          author: { id: 12, nickname: '海风' },
          social: { liked: false, bookmarked: true, likeCount: 2, bookmarkCount: 1 },
        },
      },
    })

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('social-action')).toEqual([
      [{ type: 'like', id: 8, enabled: true }],
      [{ type: 'bookmark', id: 8, enabled: false }],
    ])
    expect(wrapper.emitted('open')).toBeUndefined()
  })
})
