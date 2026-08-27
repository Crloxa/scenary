/**
 * 轻量全局提示：window 派发事件，由 App.vue 的提示栈消费。
 * 引第三方组件库对本项目"小红书观感"是负资产（docs/01 §3.2），故手写约 20 行。
 */
export function toast(msg, type = 'info') {
  window.dispatchEvent(new CustomEvent('app-toast', { detail: { msg, type } }))
}
