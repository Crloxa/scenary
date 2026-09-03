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
})
