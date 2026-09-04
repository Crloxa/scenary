// P10 读取链路压力基线：默认 10 个并发、10 秒，输出 p50/p95/p99、吞吐和错误率。
// 仅使用临时账号和 GET 请求，不写入评论/点赞/关注数据。
import { performance } from 'node:perf_hooks';

const API = process.env.SCENARY_LOAD_BASE_URL ?? 'http://localhost:8081/api/v1';
const DURATION_MS = Number(process.env.SCENARY_LOAD_DURATION_MS ?? 10000);
const CONCURRENCY = Number(process.env.SCENARY_LOAD_CONCURRENCY ?? 10);
const ACCESS_TOKEN = process.env.SCENARY_LOAD_ACCESS_TOKEN ?? '';

if (!Number.isFinite(DURATION_MS) || DURATION_MS < 1000 || DURATION_MS > 120000) {
  throw new Error('SCENARY_LOAD_DURATION_MS must be between 1000 and 120000');
}
if (!Number.isInteger(CONCURRENCY) || CONCURRENCY < 1 || CONCURRENCY > 50) {
  throw new Error('SCENARY_LOAD_CONCURRENCY must be between 1 and 50');
}

const request = async (path, options = {}) => {
  const started = performance.now();
  try {
    const response = await fetch(`${API}${path}`, options);
    const body = await response.json();
    return { latency: performance.now() - started, ok: response.ok && body.code === 0, body };
  } catch {
    return { latency: performance.now() - started, ok: false, body: null };
  }
};

const health = await request('/ping');
if (!health.ok) throw new Error('health check /ping failed before load');

let token = ACCESS_TOKEN;
if (!token) {
  const suffix = `${Date.now().toString(36).slice(-8)}${Math.random().toString(36).slice(2, 5)}`;
  const registered = await request('/auth/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: `p10load_${suffix}`, password: `Load9${suffix}x`, nickname: 'P10压力用户' }),
  });
  if (!registered.ok) throw new Error('temporary load user registration failed');
  token = registered.body.data.accessToken;
}

const headers = token ? { Authorization: `Bearer ${token}` } : {};
const targets = ['/notifications?limit=1', '/ping'];
const samples = [];
const startedAt = performance.now();
const deadline = startedAt + DURATION_MS;

async function worker(index) {
  let requestIndex = index;
  while (performance.now() < deadline) {
    const result = await request(targets[requestIndex % targets.length], { headers });
    samples.push(result);
    requestIndex += CONCURRENCY;
  }
}

await Promise.all(Array.from({ length: CONCURRENCY }, (_, index) => worker(index)));
const sorted = samples.map(item => item.latency).sort((a, b) => a - b);
const percentile = ratio => sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * ratio))] : 0;
const successes = samples.filter(item => item.ok).length;
const elapsedSeconds = (performance.now() - startedAt) / 1000;
const finalHealth = await request('/ping');

console.log(JSON.stringify({
  api: API,
  durationMs: Math.round(elapsedSeconds * 1000),
  concurrency: CONCURRENCY,
  requests: samples.length,
  successes,
  errors: samples.length - successes,
  errorRate: samples.length ? Number(((samples.length - successes) / samples.length).toFixed(4)) : 0,
  throughputRps: Number((samples.length / elapsedSeconds).toFixed(2)),
  latencyMs: {
    p50: Number(percentile(0.50).toFixed(2)),
    p95: Number(percentile(0.95).toFixed(2)),
    p99: Number(percentile(0.99).toFixed(2)),
    max: Number((sorted.at(-1) ?? 0).toFixed(2)),
  },
  healthAfterLoad: finalHealth.ok,
}, null, 2));

if (!samples.length || samples.some(item => !item.ok) || !finalHealth.ok) process.exitCode = 1;
