// P12-E1 黑盒：预签名分片 PUT、刷新恢复查询、合并幂等、取消和过期状态。
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../..', import.meta.url));
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8081/api/v1';
const pass = [];
const fail = [];
const ok = (name, condition, detail = '') => {
  (condition ? pass : fail).push(name);
  console.log(`${condition ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`);
};
const json = async response => response.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options));

function compose(args) {
  const result = spawnSync('docker', ['compose', ...args], { cwd: ROOT, encoding: 'utf8' });
  if (result.status !== 0) throw new Error('docker compose command failed');
  return result.stdout.trim();
}

function sql(query) {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p12-e1-upload', 'scenary', query,
  ]);
}

async function register(username, password) {
  const body = await request('/auth/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname: 'P12E1验收用户' }),
  });
  if (body.code !== 0) throw new Error(`register failed code=${body.code}`);
  return body.data;
}

async function waitVideo(token, mediaId) {
  for (let i = 0; i < 60; i += 1) {
    const state = await request(`/media/${mediaId}`, { headers: headers(token) });
    if (state.data?.status === 12) return state.data;
    if ([13, 14].includes(state.data?.status)) throw new Error(`video status=${state.data.status}`);
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error('video processing timeout');
}

let sessionId;
try {
  const suffix = Date.now().toString(36).slice(-7);
  const password = `T9${Date.now().toString(36)}a!`;
  const author = await register(`p12e1_${suffix}`, password);
  const video = readFileSync(`${ROOT}/docs/dev/fixtures/p12-short.mp4`);

  const created = await request('/media/video-uploads', {
    method: 'POST',
    headers: { ...headers(author.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ fileName: 'p12-e1.mp4', sizeBytes: video.length, mime: 'video/mp4' }),
  });
  sessionId = created.data?.uploadId;
  ok('① 创建 V8 视频上传会话', created.code === 0 && !!sessionId
    && created.data.status === 0 && created.data.totalParts === 1
    && created.data.chunkSize === 8388608);

  const signed = await request(`/media/video-uploads/${sessionId}/parts/1/url`, {
    method: 'POST', headers: headers(author.accessToken),
  });
  const put = signed.code === 0
    ? await fetch(signed.data.url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: video,
    })
    : null;
  ok('② 预签名 URL 直传分片', signed.code === 0 && put?.status >= 200 && put.status < 300);

  const resumed = await request(`/media/video-uploads/${sessionId}`, {
    headers: headers(author.accessToken),
  });
  ok('③ 刷新恢复可读到已上传分片', resumed.code === 0 && resumed.data.status === 0
    && resumed.data.uploadedParts?.length === 1
    && resumed.data.uploadedParts[0].partNumber === 1
    && resumed.data.uploadedParts[0].sizeBytes === video.length);

  const completed = await request(`/media/video-uploads/${sessionId}/complete`, {
    method: 'POST', headers: headers(author.accessToken),
  });
  const media = completed.data?.items?.[0];
  ok('④ 服务端合并校验后进入视频处理', completed.code === 0 && media?.status === 11
    && media.mediaType === 'VIDEO' && !JSON.stringify(media).includes('/orig/'));

  const repeated = await request(`/media/video-uploads/${sessionId}/complete`, {
    method: 'POST', headers: headers(author.accessToken),
  });
  ok('⑤ complete 幂等且不重复创建媒体', repeated.code === 0
    && repeated.data.items[0].mediaId === media.mediaId);

  const ready = await waitVideo(author.accessToken, media.mediaId);
  // E2（02 §1.5）：播放地址为签名 URL，改按对象路径断言
  const pathOf = u => { try { return new URL(u).pathname; } catch { return ''; } };
  ok('⑥ 合并产物沿用 P12 转码链路', ready.status === 12
    && pathOf(ready.playbackUrl).endsWith('_720.mp4') && pathOf(ready.playbackLowUrl).endsWith('_480.mp4'));

  const afterComplete = await request(`/media/video-uploads/${sessionId}`, {
    headers: headers(author.accessToken),
  });
  ok('⑦ 会话进入 COMPLETED 且不再暴露分片对象', afterComplete.code === 0
    && afterComplete.data.status === 2 && afterComplete.data.uploadedParts.length === 0);

  const incomplete = await request('/media/video-uploads', {
    method: 'POST',
    headers: { ...headers(author.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ fileName: 'missing.mp4', sizeBytes: video.length, mime: 'video/mp4' }),
  });
  const missingId = incomplete.data.uploadId;
  const missingComplete = await request(`/media/video-uploads/${missingId}/complete`, {
    method: 'POST', headers: headers(author.accessToken),
  });
  ok('⑧ 缺片合并返回稳定 40903 且会话可重试', missingComplete.code === 40903);
  const cancelled = await request(`/media/video-uploads/${missingId}`, {
    method: 'DELETE', headers: headers(author.accessToken),
  });
  const gone = await request(`/media/video-uploads/${missingId}`, {
    headers: headers(author.accessToken),
  });
  ok('⑨ 取消会话删除会话记录', cancelled.code === 0 && gone.code === 40400);

  const expired = await request('/media/video-uploads', {
    method: 'POST',
    headers: { ...headers(author.accessToken), 'content-type': 'application/json' },
    body: JSON.stringify({ fileName: 'expired.mp4', sizeBytes: video.length, mime: 'video/mp4' }),
  });
  const expiredId = expired.data.uploadId;
  sql(`UPDATE video_upload_sessions SET expires_at=NOW(3)-INTERVAL 1 MINUTE WHERE upload_id='${expiredId}'`);
  const expiredStatus = await request(`/media/video-uploads/${expiredId}`, {
    headers: headers(author.accessToken),
  });
  const expiredUrl = await request(`/media/video-uploads/${expiredId}/parts/1/url`, {
    method: 'POST', headers: headers(author.accessToken),
  });
  ok('⑩ 过期会话转 EXPIRED 并拒绝新预签名', expiredStatus.code === 0
    && expiredStatus.data.status === 3 && expiredUrl.code === 40902);
  await request(`/media/video-uploads/${expiredId}`, {
    method: 'DELETE', headers: headers(author.accessToken),
  });

  const v8 = sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='8' AND success=1");
  const tables = sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='scenary' AND table_name IN ('video_upload_sessions','video_upload_parts')");
  ok('⑪ V8 会话与分片表已应用', v8 === '1' && tables === '2', `v8=${v8}; tables=${tables}`);
} catch (error) {
  ok('P12-E1 黑盒执行完成', false, error.message);
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====\n`);
process.exit(fail.length ? 1 : 0);
