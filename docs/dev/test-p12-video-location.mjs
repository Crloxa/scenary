// P12 黑盒：视频魔数/状态、ffmpeg 产物、地点分享与 EXIF 隐私、队列隔离。
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
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const json = async response => response.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options));

function compose(args) {
  const result = spawnSync('docker', ['compose', ...args], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  if (result.status !== 0) throw new Error('docker compose command failed');
  return result.stdout.trim();
}

function sql(query) {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p12-video-location', 'scenary', query,
  ]);
}

async function register(username, password, nickname) {
  const body = await request('/auth/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname }),
  });
  if (body.code !== 0) throw new Error(`register failed code=${body.code}`);
  return body.data;
}

async function waitVideo(token, mediaId) {
  for (let i = 0; i < 60; i += 1) {
    const state = await request(`/media/${mediaId}`, { headers: headers(token) });
    if (state.data?.status === 12) return state.data;
    if (state.data?.status === 13 || state.data?.status === 14) {
      throw new Error(`video status=${state.data.status}`);
    }
    await sleep(500);
  }
  throw new Error('video processing timeout');
}

async function uploadVideo(token, bytes) {
  const form = new FormData();
  form.append('file', new Blob([bytes], { type: 'video/mp4' }), 'p12-short.mp4');
  return request('/media/videos', { method: 'POST', headers: headers(token), body: form });
}

async function uploadImage(token, bytes, name = 'p12.jpg') {
  const form = new FormData();
  form.append('files', new Blob([bytes], { type: 'image/jpeg' }), name);
  const body = await request('/media/images', { method: 'POST', headers: headers(token), body: form });
  if (body.code !== 0) throw new Error(`image upload failed code=${body.code}`);
  const mediaId = body.data.items[0].mediaId;
  for (let i = 0; i < 30; i += 1) {
    const state = await request(`/media/${mediaId}`, { headers: headers(token) });
    if (state.data?.status === 1) return mediaId;
    if (state.data?.status === 2) throw new Error('image processing failed');
    await sleep(300);
  }
  throw new Error('image processing timeout');
}

async function publish(token, mediaId, extra = {}) {
  const body = await request('/notes', {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({
      title: `P12验收-${Date.now()}`,
      content: '视频与地点基础链路验收',
      placeName: extra.placeName ?? '四姑娘山',
      mediaIds: [mediaId],
      visibility: 1,
      ...extra,
    }),
  });
  if (body.code !== 0) throw new Error(`publish failed code=${body.code}`);
  return body.data.id;
}

let author;
try {
  const suffix = Date.now().toString(36).slice(-7);
  const password = `T9${Date.now().toString(36)}a!`;
  author = await register(`p12a_${suffix}`, password, `P12作者${suffix}`);
  const video = readFileSync(`${ROOT}/docs/dev/fixtures/p12-short.mp4`);
  const gpsJpeg = readFileSync(`${ROOT}/docs/dev/fixtures/p12-gps.jpg`);

  const fake = await uploadVideo(author.accessToken, Buffer.from('not-a-video'));
  ok('① 视频内容伪装按魔数拒绝', fake.code === 40000);

  const uploaded = await uploadVideo(author.accessToken, video);
  const item = uploaded.data?.items?.[0];
  ok('② 合法 MP4 返回 VIDEO 且进入独立处理中状态',
    uploaded.code === 0 && item?.mediaType === 'VIDEO' && item.status === 11);
  ok('② 原始视频不进入上传响应',
    item && !JSON.stringify(item).includes('/orig/') && !JSON.stringify(item).includes('p12-short.mp4'));

  const ready = await waitVideo(author.accessToken, item.mediaId);
  // E2（02 §1.5）：播放地址为签名 URL，改按对象路径断言
  const pathOf = u => { try { return new URL(u).pathname; } catch { return ''; } };
  ok('③ ffprobe/ffmpeg 产出 READY、封面和双码率播放地址',
    ready.status === 12 && ready.durationMs > 0 && ready.width === 640 && ready.height === 360
      && ready.url?.includes('/thumb/') && pathOf(ready.playbackUrl).endsWith('_720.mp4')
      && pathOf(ready.playbackLowUrl).endsWith('_480.mp4'));
  ok('③ READY 响应仍不含原始对象路径',
    !JSON.stringify(ready).includes('/orig/') && !JSON.stringify(ready).includes('p12-short.mp4'));

  const cover = await fetch(ready.url);
  const playback = await fetch(ready.playbackUrl);
  ok('④ 封面可读且为 JPEG', cover.status === 200
    && cover.headers.get('content-type')?.startsWith('image/jpeg'));
  ok('④ 720p 产物可读且为 MP4', playback.status === 200
    && playback.headers.get('content-type')?.includes('video/mp4'));

  const mapNoteId = await publish(author.accessToken, item.mediaId, {
    title: `P12地图坐标${suffix}`,
    latitude: 30.9785,
    longitude: 102.7591,
    placeSource: 'MAP',
    placePrecision: 'EXACT',
  });
  const mapDetail = await request(`/notes/${mapNoteId}`);
  ok('⑤ 明确 MAP 分享时公开详情返回坐标',
    mapDetail.code === 0 && mapDetail.data.latitude === 30.9785
      && mapDetail.data.longitude === 102.7591
      && mapDetail.data.images[0].mediaType === 'VIDEO'
      && pathOf(mapDetail.data.images[0].playbackUrl).endsWith('_720.mp4'));

  const gpsMediaId = await uploadImage(author.accessToken, gpsJpeg);
  const exifNoteId = await publish(author.accessToken, gpsMediaId, {
    title: `P12隐私坐标${suffix}`,
    placeName: 'EXIF候选位置',
    placeSource: 'EXIF',
  });
  const exifDetail = await request(`/notes/${exifNoteId}`);
  ok('⑥ EXIF 候选坐标只用于服务端处理且不公开原始经纬度',
    exifDetail.code === 0 && exifDetail.data.placeSource === 'EXIF'
      && exifDetail.data.latitude === null && exifDetail.data.longitude === null
      && !JSON.stringify(exifDetail.data).includes('30.123456')
      && !JSON.stringify(exifDetail.data).includes('102.654321'));

  const v7 = sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='7' AND success=1");
  const columns = sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='scenary' AND table_name IN ('media','notes') AND column_name IN ('media_type','duration_ms','playback_url','playback_low_url','latitude','longitude','place_source','place_precision')");
  ok('⑦ V7 已应用且视频/地点扩展字段齐全', v7 === '1' && columns === '8', `v7=${v7}; columns=${columns}`);

  const queues = compose([
    'exec', '-T', 'rabbitmq', 'rabbitmqctl', 'list_queues', 'name',
  ]);
  ok('⑧ 图片与视频处理队列、DLQ 独立声明',
    ['media.thumbnail.q', 'media.dlq', 'video.transcode.q', 'video.dlq']
      .every(name => queues.split(/\r?\n/).includes(name)));
} catch (error) {
  ok('P12 黑盒执行完成', false, error.message);
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====\n`);
process.exit(fail.length ? 1 : 0);
