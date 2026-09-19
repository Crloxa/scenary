// P12-E3 验收用 mock Nominatim（docs/05 §6.5）：仅内网容器调用，不发布端口。
// 运行（compose 网络内）：node mock-nominatim.mjs [端口=9999]
// 行为约定：
//   ?lat=5        → 睡 5s 再响应（超时降级断言用，provider 超时 2s）
//   其他坐标      → 返回 Nominatim jsonv2 形状的固定地名
//   /metrics      → 返回收到的 /reverse 次数（缓存命中断言用）
import http from 'node:http'

const port = Number(process.argv[2] ?? 9999)
let reverseHits = 0

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`)
  if (url.pathname === '/metrics') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ reverseHits }))
    return
  }
  if (url.pathname !== '/reverse') {
    res.writeHead(404).end()
    return
  }
  reverseHits += 1
  const lat = url.searchParams.get('lat')
  const lon = url.searchParams.get('lon')
  const send = () => {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({
      name: `验收地名-${lat}`,
      display_name: `验收地名-${lat}, 验收区, 验收市 (lon=${lon})`,
    }))
  }
  if (lat !== null && Math.abs(Number(lat) - 5) < 0.05) {
    // 模拟 provider 卡死：backend 2s 超时必须先于本响应返回空候选
    // （backend 会把 5 序列化为 5.0，故按数值比较）
    setTimeout(send, 5000)
    return
  }
  send()
})

server.listen(port, () => console.log(`mock-nominatim listening on :${port}`))
