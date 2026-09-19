import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ImageEditorModal from '@/components/ImageEditorModal.vue'
import { FILTERS } from '@/utils/imageEditor'

// jsdom 无真实 canvas/图片解码：本套件验证弹窗骨架与筛选交互；
// 绘制与导出链路由 Chromium 端到端与手测覆盖（docs/05 §13 验收边界）。
describe('ImageEditorModal', () => {
  it('无文件时不渲染预览但滤镜与比例控件可用，取消即关闭', async () => {
    const wrapper = mount(ImageEditorModal, { props: { file: null } })
    expect(wrapper.find('[data-testid="editor-preview"]').isVisible()).toBe(false)
    expect(wrapper.text()).toContain('图片加载中…')

    const filters = wrapper.findAll('[aria-label="滤镜"] button')
    expect(filters).toHaveLength(FILTERS.length)
    await filters[1].trigger('click')
    expect(filters[1].attributes('aria-pressed')).toBe('true')

    // 未加载完成前不可应用
    expect(wrapper.get('[data-testid="editor-apply"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[aria-label="关闭编辑器"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})
