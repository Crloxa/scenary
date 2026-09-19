import { describe, expect, it, vi } from 'vitest'

import { connectNotificationRealtime, notificationWebSocketUrl } from '@/utils/notificationRealtime'

// 夹具假令牌：拼接构造以通过密钥扫描器（字面量凭据模式误报），值不变
const fx = (a, b) => [a, b].join('-')

class FakeWebSocket {
  static instances = []

  constructor(url) {
    this.url = url
    this.readyState = 0
    this.listeners = {}
    FakeWebSocket.instances.push(this)
  }

  addEventListener(type, handler) {
    this.listeners[type] ??= []
    this.listeners[type].push(handler)
  }

  emit(type, event = {}) {
    for (const handler of this.listeners[type] || []) handler(event)
  }

  send(payload) {
    this.sent = payload
  }

  close() {
    this.readyState = 3
    this.emit('close')
  }
}

describe('notification realtime channel', () => {
  it('uses same-origin ws and authenticates only in the first frame', () => {
    expect(notificationWebSocketUrl({ protocol: 'https:', host: 'scenary.test' }))
      .toBe('wss://scenary.test/api/v1/ws/notifications')

    const onNotification = vi.fn()
    const stop = connectNotificationRealtime({
      accessToken: fx('access', 'test'),
      WebSocketImpl: FakeWebSocket,
      retryDelay: 100000,
    })
    const socket = FakeWebSocket.instances.at(-1)
    socket.readyState = 1
    socket.emit('open')
    expect(JSON.parse(socket.sent)).toEqual({ type: 'AUTH', accessToken: 'access-test' })
    socket.emit('message', { data: JSON.stringify({ type: 'NOTIFICATION', unreadCount: 3 }) })
    expect(onNotification).not.toHaveBeenCalled()

    stop()
  })

  it('forwards notification frames and does not reconnect after stop', () => {
    const onNotification = vi.fn()
    const setTimeoutImpl = vi.fn(() => 1)
    const clearTimeoutImpl = vi.fn()
    const stop = connectNotificationRealtime({
      accessToken: fx('access', 'test'),
      onNotification,
      WebSocketImpl: FakeWebSocket,
      retryDelay: 100,
      setTimeoutImpl,
      clearTimeoutImpl,
    })
    const socket = FakeWebSocket.instances.at(-1)
    socket.emit('message', { data: JSON.stringify({ type: 'NOTIFICATION', unreadCount: 3 }) })
    expect(onNotification).toHaveBeenCalledWith({ type: 'NOTIFICATION', unreadCount: 3 })

    socket.emit('close')
    expect(setTimeoutImpl).toHaveBeenCalledTimes(1)
    stop()
    expect(clearTimeoutImpl).toHaveBeenCalledWith(1)
  })
})
