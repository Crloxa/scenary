// P12-E3 逆地理编码黑盒（docs/05 §6.5、03 附 8）
// 运行：SCENARY_API_BASE_URL=http://localhost:8081/api/v1 node docs/dev/test-p12-e3-places.mjs
//
// 两种模式（由后端容器环境决定，脚本自动探测，不设开关）：
//   A. provider 关闭（默认）：断言匿名 401、参数边界 40000、合法坐标空候选（provider=none）
//   B. provider 启用（mock nominatim，见 docs/dev/mock-nominatim.mjs）：在 A 基础上断言
//      正常候选、缓存命中（上游只被调 1 次）、超时降级（?lat=5 睡 5s > 2s 超时）、限流 429
// 用户名随机化 + 限流按用户键 → 脚本天然可重复执行；mock 的 /metrics 只增不清。

// mock 仅内网可达（无发布端口，隐私拓扑与真实 provider 一致）：
// 宿主机直连 /metrics 失败时自动走 docker exec（沿用 test-thumbnail37 的容器回退先例）
import { spawnSync } from 'node:child_process'

const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8081/api/v1'
const pass = []
const fail = []
const ok = (name, condition, detail = '') => {
  (condition ? pass : fail).push(name)
  console.log(`${condition ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`)
}
const headers = token => ({ Authorization: `Bearer ${token}` })
const json = async response => response.json()
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options))

async function register(username, password = 'Passw0rd!234') {
  const body = await request('/auth/register', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname: username }),
  })
  if (body.code !== 0 || !body.data?.accessToken) {
    throw new Error(`register failed code=${body.code} msg=${body.message}`)
  }
  return body.data
}

const geocode = async (token, lat, lng) => {
  const started = Date.now()
  const response = await fetch(`${B}/places/reverse-geocode?latitude=${lat}&longitude=${lng}`, {
    headers: headers(token),
  })
  return { status: response.status, body: await response.json(), elapsed: Date.now() - started }
}

// ---------- 准备：随机用户 ----------
const user = await register(`e3_${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6)}`)
const token = user.accessToken
ok('① 注册并获取令牌', Boolean(token))

// ---------- A 组：契约边界（provider 开关状态均适用） ----------
{
  const anon = await fetch(`${B}/places/reverse-geocode?latitude=31.2&longitude=121.4`)
  const anonBody = await anon.json()
  ok('② 匿名访问 401/40100', anon.status === 401 && anonBody.code === 40100,
    `http=${anon.status} code=${anonBody.code}`)

  const missing = await fetch(`${B}/places/reverse-geocode?latitude=31.2`, { headers: headers(token) })
  ok('③ 缺参 400/40000', missing.status === 400 && (await missing.json()).code === 40000)

  const nonNumeric = await geocode(token, 'abc', '121.4')
  ok('③ 非数值坐标 400/40000',
    nonNumeric.status === 400 && nonNumeric.body.code === 40000, `code=${nonNumeric.body.code}`)

  const outOfRange = await geocode(token, '90.1', '-180.000001')
  ok('③ 越界坐标 400/40000',
    outOfRange.status === 400 && outOfRange.body.code === 40000, `code=${outOfRange.body.code}`)

  const boundaryOk = await geocode(token, '-90', '180')
  ok('③ 边界值 (-90,180) 合法', boundaryOk.status === 200, `code=${boundaryOk.body.code}`)
}

// ---------- 模式探测：合法坐标查询 provider 字段 ----------
const probe = await geocode(token, '31.2304', '121.4737')
const providerEnabled = probe.body?.data?.provider !== 'none'
ok('④ 契约形状 {placeName,provider,cached}',
  probe.status === 200 && 'placeName' in (probe.body?.data ?? {}) && 'cached' in (probe.body?.data ?? {}),
  `provider=${probe.body?.data?.provider}`)

if (!providerEnabled) {
  ok('⑤ provider 关闭：空候选 + provider=none',
    probe.body.data.placeName === null && probe.body.data.provider === 'none' && probe.body.data.cached === false)
  console.log('\n(provider 未启用——A 组边界完成；B 组需以 SCENARY_PLACE_PROVIDER_ENABLED=true 重启 backend 后复跑)')
} else {
  // ---------- B 组：启用路径（mock nominatim） ----------
  const mockBase = process.env.SCENARY_MOCK_PROVIDER_URL ?? 'http://localhost:9999'
  const MOCK_CONTAINER = 'scenary-mock-nominatim'
  const metrics = async () => {
    try {
      return (await json(await fetch(`${mockBase}/metrics`))).reverseHits
    } catch {
      const result = spawnSync('docker',
        ['exec', MOCK_CONTAINER, 'wget', '-qO-', 'http://127.0.0.1:9999/metrics'],
        { encoding: 'utf8' })
      if (result.status !== 0) throw new Error(`metrics unavailable: ${result.stderr}`)
      return JSON.parse(result.stdout).reverseHits
    }
  }

  const cacheCellLat = (20 + Math.random() * 10).toFixed(6)
  const cacheCellLng = (100 + Math.random() * 10).toFixed(6)
  const hitsBefore = await metrics()

  const first = await geocode(token, cacheCellLat, cacheCellLng)
  ok('⑥ 正常候选：placeName 来自 provider',
    first.body.code === 0 && String(first.body.data.placeName ?? '').startsWith('验收地名-')
      && first.body.data.provider === 'nominatim' && first.body.data.cached === false,
    `placeName=${first.body.data.placeName}`)

  const second = await geocode(token, cacheCellLat, cacheCellLng)
  const hitsAfter = await metrics()
  ok('⑦ 缓存命中：第二次 cached=true 且上游未被再次调用',
    second.body.data.cached === true && hitsAfter === hitsBefore + 1,
    `upstreamHits=${hitsAfter - hitsBefore}`)

  const timeout = await geocode(token, '5', '5')
  ok('⑧ 超时降级：空候选且 200、耗时≈超时上限（2s）不阻塞',
    timeout.body.code === 0 && timeout.body.data.placeName === null
      && timeout.body.data.cached === false && timeout.elapsed < 4500,
    `elapsed=${timeout.elapsed}ms`)

  let saw429 = null
  let rate429message = ''
  for (let i = 0; i < 40; i += 1) {
    const attempt = await geocode(token, (-(60 + i * 0.3)).toFixed(4), (-(170 + i * 0.2)).toFixed(4))
    if (attempt.status === 429 && attempt.body.code === 42001) {
      saw429 = i
      rate429message = attempt.body.message
      break
    }
  }
  ok('⑨ 限流 30/min 触发 429/42001（message 含剩余秒数）',
    saw429 !== null && /后再试/.test(rate429message),
    `429 在第 ${saw429} 次触发, msg=${rate429message}`)
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`)
process.exit(fail.length ? 1 : 0)
