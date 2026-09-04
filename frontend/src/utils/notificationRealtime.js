/**
 * 原生通知 WebSocket：只负责实时刷新信号，HTTP 通知分页仍是可靠数据源。
 * 连接失败时由调用方继续保留轮询；此处只做有限频率的自动重连。
 */
export function notificationWebSocketUrl(location = window.location) {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/api/v1/ws/notifications`
}

export function connectNotificationRealtime({
  accessToken,
  onNotification = () => {},
  onStateChange = () => {},
  WebSocketImpl = window.WebSocket,
  retryDelay = 5000,
  setTimeoutImpl = window.setTimeout,
  clearTimeoutImpl = window.clearTimeout,
} = {}) {
  if (!accessToken || !WebSocketImpl) return () => {}

  let stopped = false
  let socket
  let retryTimer

  const connect = () => {
    if (stopped) return
    onStateChange('connecting')
    try {
      socket = new WebSocketImpl(notificationWebSocketUrl())
      socket.addEventListener('open', () => {
        socket.send(JSON.stringify({ type: 'AUTH', accessToken }))
        onStateChange('connected')
      })
      socket.addEventListener('message', event => {
        try {
          const message = JSON.parse(event.data)
          if (message.type === 'NOTIFICATION') onNotification(message)
        } catch {
          // 忽略无法解析的增强通道消息，HTTP 查询仍可用
        }
      })
      socket.addEventListener('error', () => onStateChange('fallback'))
      socket.addEventListener('close', () => {
        if (stopped) return
        onStateChange('fallback')
        retryTimer = setTimeoutImpl(connect, retryDelay)
      })
    } catch {
      onStateChange('fallback')
      retryTimer = setTimeoutImpl(connect, retryDelay)
    }
  }

  connect()
  return () => {
    stopped = true
    if (retryTimer) clearTimeoutImpl(retryTimer)
    if (socket && (socket.readyState === 0 || socket.readyState === 1)) socket.close()
  }
}
