// P15 账号与安全基线黑盒：安全响应头、写接口限流（注册/社交/评论）、账号注销全链路、
// 通知保留策略配置注入。注册限流段会触发阈值并自行清理 Redis 计数键，可重复执行。
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../..', import.meta.url));
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8081/api/v1';
const REGISTER_LIMIT = Number(process.env.SCENARY_RATELIMIT_REGISTER_PER_HOUR ?? 200);
const pass = [];
const fail = [];
const ok = (name, condition, detail = '') => {
  (condition ? pass : fail).push(name);
  console.log(`${condition ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`);
};

function compose(args) {
  const result = spawnSync('docker', ['compose', ...args], { cwd: ROOT, encoding: 'utf8' });
  if (result.status !== 0) throw new Error(`docker compose command failed: ${args.join(' ')}`);
  return result.stdout.trim();
}

const rnd = Math.random().toString(36).slice(2, 8);
const headers = token => ({ Authorization: `Bearer ${token}` });
const json = async response => response.json();
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options));

async function register(username, password = 'Passw0rd!234') {
  const body = await request('/auth/register', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname: username }),
  });
  if (body.code !== 0 || !body.data?.accessToken) {
    throw new Error(`register failed code=${body.code} msg=${body.message}`);
  }
  return body.data;
}

// ---- ① 安全响应头（P15-02）----
{
  const res = await fetch('http://localhost:8081/');
  const h = name => res.headers.get(name);
  const cspOk = (h('content-security-policy-report-only') ?? '').includes("default-src 'self'");
  ok('① 安全响应头齐全', h('x-frame-options') === 'DENY'
    && h('x-content-type-options') === 'nosniff'
    && h('referrer-policy') === 'strict-origin-when-cross-origin'
    && cspOk,
    `XFO=${h('x-frame-options')} XCTO=${h('x-content-type-options')} RP=${h('referrer-policy')} CSP-RO=${cspOk}`);
  // HSTS 仅在 TLS 部署形态开启，http 开发环境预期缺失
  ok('① http 形态无 HSTS（预期）', h('strict-transport-security') === null);
}

// ---- ② 社交写限流 120/min（P15-01）----
{
  const reg = await register(`p15soc_${rnd}`);
  let saw429 = false;
  let codes = {};
  for (let i = 0; i < 125; i++) {
    const res = await fetch(`${B}/notes/999999999/like`, {
      method: 'PUT', headers: headers(reg.accessToken),
    });
    codes[res.status] = (codes[res.status] || 0) + 1;
    if (res.status === 429) saw429 = true;
  }
  ok('② 社交写接口触发 42001', saw429, '125 次点赞（目标笔记不存在）至少一次 429; 状态码分布=' + JSON.stringify(codes));
}

// ---- ③ 评论限流回归 20/min（既有契约不变）----
{
  const reg = await register(`p15cmt_${rnd}`);
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'p15.png');
  const uploaded = await request('/media/images', { method: 'POST', headers: headers(reg.accessToken), body: form });
  const mediaId = uploaded.data.items[0].mediaId;
  let ready = false;
  for (let i = 0; i < 30 && !ready; i++) {
    await new Promise(r => setTimeout(r, 1000));
    const q = await request(`/media/${mediaId}`, { headers: headers(reg.accessToken) });
    ready = Boolean(q.data?.thumbUrl);
  }
  const note = await request('/notes', {
    method: 'POST', headers: { ...headers(reg.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ title: 'P15 限流夹具', content: 'p15', mediaIds: [mediaId], visibility: 0 }),
  });
  let limited = false;
  for (let i = 0; i < 22; i++) {
    const res = await fetch(`${B}/notes/${note.data.id}/comments`, {
      method: 'POST', headers: { ...headers(reg.accessToken), 'content-type': 'application/json' },
      body: JSON.stringify({ content: `c${i}` }),
    });
    if (res.status === 429) limited = true;
  }
  ok('③ 评论限流 20/min 回归', limited, '22 条评论至少一次 429');
}

// ---- ④ 账号注销全链路（P15-04）----
{
  const reg = await register(`p15del_${rnd}`);
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'p15.png');
  const uploaded = await request('/media/images', { method: 'POST', headers: headers(reg.accessToken), body: form });
  const mediaId = uploaded.data.items[0].mediaId;
  let ready = false;
  for (let i = 0; i < 30 && !ready; i++) {
    await new Promise(r => setTimeout(r, 1000));
    const q = await request(`/media/${mediaId}`, { headers: headers(reg.accessToken) });
    ready = Boolean(q.data?.thumbUrl);
  }
  const note = await request('/notes', {
    method: 'POST', headers: { ...headers(reg.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ title: 'P15 注销夹具', content: 'p15', mediaIds: [mediaId], visibility: 1 }),
  });

  const wrong = await fetch(`${B}/users/me`, {
    method: 'DELETE', headers: { ...headers(reg.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ password: 'WrongPass123' }),
  });
  ok('④ 注销密码二次确认失败 40300', wrong.status === 403, `status=${wrong.status}`);

  const done = await request('/users/me', {
    method: 'DELETE', headers: { ...headers(reg.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ password: 'Passw0rd!234' }),
  });
  ok('④ 注销成功返回 deactivated', done.code === 0 && done.data?.deactivated === true, `code=${done.code}`);

  const stale = await fetch(`${B}/users/me`, { headers: headers(reg.accessToken) });
  ok('④ 注销后旧令牌即时失效', stale.status === 401, `status=${stale.status}`);

  const rel = await request('/auth/login', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: `p15del_${rnd}`, password: 'Passw0rd!234' }),
  });
  ok('④ 注销后登录并入 40301', rel.code === 40301, `code=${rel.code}`);

  const noteAnon = await fetch(`${B}/notes/${note.data.id}`);
  ok('④ 注销后公开笔记不可见', noteAnon.status === 404, `status=${noteAnon.status}`);

  const profile = await fetch(`${B}/users/${reg.userId}`);
  ok('④ 注销后主页 40400', profile.status === 404, `status=${profile.status}`);
}

// ---- ⑤ 注册限流触发与计数清理（P15-01）----
{
  let success = 0;
  let lastStatuses = [];
  for (let i = 0; i < REGISTER_LIMIT + 5; i++) {
    const res = await fetch(`${B}/auth/register`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ username: `p15rl${i}_${rnd}`, password: 'Passw0rd!234' }),
    });
    if (res.status === 200) success++;
    lastStatuses.push(res.status);
    if (lastStatuses.length > 3) lastStatuses.shift();
  }
  const tripped = lastStatuses.every(s => s === 429);
  ok('⑤ 注册限流按 IP 触发 429', success >= 1 && tripped,
    `成功 ${success} 次，末尾状态 ${lastStatuses.join(',')}`);

  compose(['exec', '-T', 'redis', 'sh', '-lc',
    'redis-cli --scan --pattern "rl:register:*" | xargs -r redis-cli del >/dev/null']);
  const after = await register(`p15after_${rnd}`);
  ok('⑤ 清理计数键后注册恢复', Boolean(after.userId), `userId=${after.userId}`);
}

// ---- ⑥ 通知保留策略配置注入（P15-05）----
{
  const env = compose(['exec', '-T', 'backend', 'sh', '-lc',
    'printenv SCENARY_NOTIFICATION_RETENTION_DAYS; printenv SCENARY_NOTIFICATION_CLEANUP_ENABLED']);
  const lines = env.split('\n').map(s => s.trim()).filter(Boolean);
  ok('⑥ 通知保留策略配置注入', lines.includes('90') && lines.includes('true'), env.replace(/\n/g, ' '));
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
if (fail.length) {
  console.log(`失败项：\n- ${fail.join('\n- ')}`);
  process.exit(1);
}
