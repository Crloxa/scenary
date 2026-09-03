import { afterEach, vi } from 'vitest'

class NoopObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (!globalThis.IntersectionObserver) globalThis.IntersectionObserver = NoopObserver
if (!globalThis.ResizeObserver) globalThis.ResizeObserver = NoopObserver
if (!window.matchMedia) {
  window.matchMedia = () => ({
    matches: false,
    media: '',
    onchange: null,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent() { return false },
  })
}

afterEach(() => {
  document.documentElement.className = ''
  localStorage.clear()
  vi.useRealTimers()
})
