/** 主题切换：html.dark 单开关。index.html 内联脚本已做首屏防闪烁预判，此模块负责切换与记忆。 */
const KEY = 'scenary-theme'

export function isDark() {
  return document.documentElement.classList.contains('dark')
}

export function toggleTheme() {
  const next = isDark() ? 'light' : 'dark'
  document.documentElement.classList.toggle('dark', next === 'dark')
  localStorage.setItem(KEY, next)
  return next
}

export function preferredDark() {
  const saved = localStorage.getItem(KEY)
  if (saved) return saved === 'dark'
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}
