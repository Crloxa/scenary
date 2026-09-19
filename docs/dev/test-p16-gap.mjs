// P16 夹缝任务包：笔记编辑（媒体全量替换/越权/软删边界）、关注者与正在关注列表
// （游标分页/匿名视角/40400 边界）、OpenAPI 生产默认关闭。
// 运行：node docs/dev/test-p16-gap.mjs（需 :8081 Compose 全栈；随机用户名可重复执行）
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

function compose(args) {
  const result = spawnSync('docker', ['compose', ...args], { cwd: ROOT, encoding: 'utf8' });
  if (result.status !== 0) throw new Error('docker compose command failed');
  return result.stdout.trim();
}

function sql(query, database = 'scenary') {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p16-gap', database, query,
  ]);
}

const json = async response => response.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const stamp = Date.now().toString(36);
const rand = Math.random().toString(36).slice(2, 8);

async function register(tag) {
  const username = `p16${tag}${stamp}${rand}`;
  const body = await json(await fetch(`${B}/auth/register`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password: `P16-gap-${stamp}`, nickname: `P16-${tag}` }),
  }));
  if (body.code !== 0) throw new Error(`register failed code=${body.code}`);
  return body.data;
}

async function uploadReady(token) {
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'p16.png');
  const uploaded = await json(await fetch(`${B}/media/images`, {
    method: 'POST', headers: headers(token), body: form,
  }));
  if (uploaded.code !== 0) throw new Error(`upload failed code=${uploaded.code}`);
  const mediaId = uploaded.data.items[0].mediaId;
  for (let i = 0; i < 30; i += 1) {
    const state = await json(await fetch(`${B}/media/${mediaId}`, { headers: headers(token) }));
    if (state.data.status === 1) return mediaId;
    if (state.data.status === 2) throw new Error('media processing failed');
    await sleep(300);
  }
  throw new Error('media processing timeout');
}

async function publish(token, mediaIds, title, visibility = 1) {
  const body = await json(await fetch(`${B}/notes`, {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ title, content: 'P16 编辑验收夹具', mediaIds, visibility }),
  }));
  if (body.code !== 0) throw new Error(`publish failed code=${body.code}`);
  return body.data.id;
}

const edit = async (token, noteId, payload) => json(await fetch(`${B}/notes/${noteId}`, {
  method: 'PUT',
  headers: { ...headers(token), 'content-type': 'application/json' },
  body: JSON.stringify(payload),
}));

const detail = async (token, noteId) => json(
  await fetch(`${B}/notes/${noteId}`, token ? { headers: headers(token) } : {}),
);

const list = async (token, userId, dir, query = '') => json(
  await fetch(`${B}/users/${userId}/${dir}${query}`, token ? { headers: headers(token) } : {}),
);

// ---------- ① 笔记编辑 ----------
const author = await register('a');
const other = await register('b');
const authorToken = author.accessToken;
const otherToken = other.accessToken;

const m1 = await uploadReady(authorToken);
const m2 = await uploadReady(authorToken);
const noteId = await publish(authorToken, [m1], 'P16 原始标题');

{
  const foreign = await edit(otherToken, noteId, {
    title: '越权', content: '', placeName: '', mediaIds: [m2], visibility: 1,
  });
  ok('① 他人编辑返回 40300', foreign.code === 40300, `code=${foreign.code}`);

  const missingMedia = await edit(authorToken, noteId, {
    title: '占', content: '', placeName: '', mediaIds: [999999999], visibility: 1,
  });
  ok('① 不存在的媒体返回 40400', missingMedia.code === 40400, `code=${missingMedia.code}`);

  const replaced = await edit(authorToken, noteId, {
    title: 'P16 编辑后标题',
    content: 'P16 编辑后正文',
    placeName: '杭州·西湖',
    mediaIds: [m2],
    visibility: 1,
  });
  ok('① 编辑返回 code=0', replaced.code === 0, `code=${replaced.code}`);

  const after = await detail(authorToken, noteId);
  ok('① 编辑后标题/正文/地点生效',
    after.data.title === 'P16 编辑后标题' && after.data.content === 'P16 编辑后正文'
    && after.data.placeName === '杭州·西湖');
  ok('① 媒体集合替换为 [m2]', after.data.images.length === 1
    && after.data.images[0].mediaId === m2, `images=${after.data.images.map(i => i.mediaId)}`);
  ok('① coverUrl 为短时签名', after.data.images[0].thumbUrl.includes('X-Amz-Signature')
    || after.data.images[0].thumbUrl.includes('sign='), after.data.images[0].thumbUrl.slice(0, 48));

  const unbound = sql(`SELECT IF(note_id IS NULL, 'NULL', note_id) FROM media WHERE id=${m1}`);
  ok('① 被替换媒体解绑回游离态', unbound === 'NULL', `note_id=${unbound}`);

  // feed 两级缓存为最终一致（ HANDOVER §5、01 §5.3）：轮询至多 3s 等待首页刷新
  let feedHit = false;
  for (let i = 0; i < 20 && !feedHit; i += 1) {
    const feed = await json(await fetch(`${B}/feed?limit=20`));
    feedHit = (feed.data.list ?? []).some(
      card => card.id === noteId && card.title === 'P16 编辑后标题');
    if (!feedHit) await sleep(150);
  }
  ok('① 首页卡片呈现编辑后标题', feedHit);

  const rebound = await edit(authorToken, noteId, {
    title: 'P16 再编辑', content: '', placeName: '', mediaIds: [m1, m2], visibility: 1,
  });
  const reboundDetail = await detail(authorToken, noteId);
  ok('① 游离媒体可重新加入（全量替换语义）', rebound.code === 0
    && reboundDetail.data.images.length === 2
    && reboundDetail.data.images[0].mediaId === m1, `images=${reboundDetail.data.images.map(i => i.mediaId)}`);

  const secret = await edit(authorToken, noteId, {
    title: 'P16 私密', content: '', placeName: '', mediaIds: [m1, m2], visibility: 0,
  });
  const anon = await detail(null, noteId);
  const owner = await detail(authorToken, noteId);
  ok('① 可见性可编辑：匿名 40400、作者可见', secret.code === 0 && anon.code === 40400
    && owner.data.visibility === 0, `anon=${anon.code}`);

  const back = await edit(authorToken, noteId, {
    title: 'P16 公开', content: '', placeName: '', mediaIds: [m1, m2], visibility: 1,
  });
  ok('① 恢复公开 code=0', back.code === 0);
}

{
  const m3 = await uploadReady(authorToken);
  const doomed = await publish(authorToken, [m3], 'P16 待删除');
  await json(await fetch(`${B}/notes/${doomed}`, { method: 'DELETE', headers: headers(authorToken) }));
  const deleted = await edit(authorToken, doomed, {
    title: 'x', content: '', placeName: '', mediaIds: [m3], visibility: 1,
  });
  ok('① 软删笔记编辑返回 40400', deleted.code === 40400, `code=${deleted.code}`);
}

// ---------- ② 关注者 / 正在关注列表 ----------
const u1 = await register('u1');
const u2 = await register('u2');
const u3 = await register('u3');
const u4 = await register('u4');
for (const u of [u1, u2, u3]) {
  const r = await json(await fetch(`${B}/users/${u4.userId}/follow`, {
    method: 'PUT', headers: headers(u.accessToken),
  }));
  if (r.code !== 0) throw new Error(`follow failed code=${r.code}`);
}
// u1 额外关注 u2：让 u2 的关注者列表出现 following=true 的成员视角
await json(await fetch(`${B}/users/${u2.userId}/follow`, {
  method: 'PUT', headers: headers(u1.accessToken),
}));

{
  const anonList = await list(null, u4.userId, 'followers');
  ok('② 匿名可看关注者列表', anonList.code === 0 && anonList.data.list.length === 3,
    `n=${anonList.data?.list?.length}`);
  ok('② 匿名视角 following 恒 false', anonList.data.list.every(m => m.following === false));

  const viewerList = await list(u1.accessToken, u4.userId, 'followers');
  const memberU2 = viewerList.data.list.find(m => m.id === u2.userId);
  const memberU1 = viewerList.data.list.find(m => m.id === u1.userId);
  ok('② 登录视角 following 差异化', memberU2?.following === true && memberU1?.following === false,
    `u2=${memberU2?.following} u1=${memberU1?.following}`);

  const page1 = await list(null, u4.userId, 'followers', '?limit=2');
  const page2 = await list(null, u4.userId, 'followers',
    `?limit=2&cursor=${page1.data.nextCursor}`);
  const ids1 = page1.data.list.map(m => m.id);
  const ids2 = page2.data.list.map(m => m.id);
  ok('② 游标分页不重不漏', page1.data.hasMore === true && page1.data.nextCursor != null
    && ids1.length === 2 && ids2.length === 1
    && !ids1.some(id => ids2.includes(id)), `p1=${ids1} p2=${ids2}`);

  const followingList = await list(null, u1.userId, 'following');
  ok('② 正在关注列表含 u4 与 u2', followingList.code === 0
    && followingList.data.list.some(m => m.id === u4.userId)
    && followingList.data.list.some(m => m.id === u2.userId));

  const missing = await list(null, 999999999, 'followers');
  ok('② 不存在用户返回 40400', missing.code === 40400, `code=${missing.code}`);

  const avatarOk = viewerList.data.list.every(m => m.avatarUrl == null
    || (typeof m.avatarUrl === 'string'
      && (m.avatarUrl === '' || m.avatarUrl.includes('/'))));
  ok('② 列表头像字段为签名 URL 结构', avatarOk);
}

// ---------- ③ OpenAPI 生产默认关闭 ----------
{
  const res = await fetch('http://localhost:8081/api/v1/api-docs');
  ok('③ Compose 生产形态 api-docs 关闭（404）', res.status === 404, `status=${res.status}`);
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
process.exit(fail.length ? 1 : 0);
