// P18 社区治理最小闭环：举报去重/阈值自动隐藏、评论举报、屏蔽双向过滤、限流与幂等。
// 运行：SCENARY_API_BASE_URL=http://localhost:8081/api/v1 node docs/dev/test-p18-governance.mjs
// 需 Compose 全栈（V10 迁移）；随机用户名可重复执行。
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../..', import.meta.url));
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8081/api/v1';
const pass = [];
const fail = [];
const ok = (name, condition, detail = '') => {
  (condition ? pass : fail).push(name);
  console.log(`${condition ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`);
};

const json = async r => r.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const stamp = Date.now().toString(36);
const rand = Math.random().toString(36).slice(2, 8);

async function register(tag) {
  const body = await json(await fetch(`${B}/auth/register`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      username: `p18${tag}${stamp}${rand}`,
      password: `P18-gov-${stamp}`,
      nickname: `P18-${tag}`,
    }),
  }));
  if (body.code !== 0) throw new Error(`register ${tag} failed code=${body.code}`);
  return body.data;
}

async function uploadReady(token) {
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'p18.png');
  const uploaded = await json(await fetch(`${B}/media/images`, {
    method: 'POST', headers: headers(token), body: form,
  }));
  const mediaId = uploaded.data.items[0].mediaId;
  for (let i = 0; i < 30; i += 1) {
    const state = await json(await fetch(`${B}/media/${mediaId}`, { headers: headers(token) }));
    if (state.data.status === 1) return mediaId;
    if (state.data.status === 2) throw new Error('media processing failed');
    await sleep(300);
  }
  throw new Error('media processing timeout');
}

async function publish(token, mediaIds, title) {
  const body = await json(await fetch(`${B}/notes`, {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ title, content: 'P18 治理验收夹具', mediaIds, visibility: 1 }),
  }));
  if (body.code !== 0) throw new Error(`publish failed code=${body.code}`);
  return body.data.id;
}

const report = async (token, targetType, targetId, reasonCode = 'SPAM') =>
  json(await fetch(`${B}/reports`, {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ targetType, targetId, reasonCode }),
  }));

const detail = async (token, noteId) =>
  json(await fetch(`${B}/notes/${noteId}`, token ? { headers: headers(token) } : {}));

const feedIds = async (token, limit = 20) => {
  const f = await json(await fetch(`${B}/feed?limit=${limit}`, token ? { headers: headers(token) } : {}));
  return (f.data?.list ?? []).map(c => c.id);
};

const grid = async (token, userId) =>
  json(await fetch(`${B}/users/${userId}/notes`, token ? { headers: headers(token) } : {}));

// ---------- ① 举报：去重 + 阈值自动隐藏 ----------
const author = await register('a');
const authorToken = author.accessToken;
const authorMedia = await uploadReady(authorToken);
const noteId = await publish(authorToken, [authorMedia], 'P18 待举报笔记');

{
  const self = await report(authorToken, 'note', noteId);
  ok('① 举报自己的笔记 40000', self.code === 40000, `code=${self.code}`);
  const bad = await report(authorToken, 'note', noteId, 'WHATEVER');
  ok('① 非法 reasonCode 40000', bad.code === 40000, `code=${bad.code}`);
}

const reporters = [];
for (let i = 1; i <= 5; i += 1) reporters.push(await register(`r${i}`));

{
  const first = await report(reporters[0].accessToken, 'note', noteId);
  ok('① 首次举报 created=true 未达阈值', first.code === 0 && first.data.created === true
    && first.data.hidden === false, JSON.stringify(first.data));

  const dup = await report(reporters[0].accessToken, 'note', noteId);
  ok('① 同用户重复举报幂等 created=false', dup.code === 0 && dup.data.created === false
    && dup.data.hidden === false, JSON.stringify(dup.data));

  for (let i = 1; i <= 3; i += 1) {
    await report(reporters[i].accessToken, 'note', noteId);
  }
  const fifth = await report(reporters[4].accessToken, 'note', noteId);
  ok('① 第 5 个不同用户举报触发自动隐藏', fifth.code === 0 && fifth.data.hidden === true,
    JSON.stringify(fifth.data));

  const anon = await detail(null, noteId);
  const owner = await detail(authorToken, noteId);
  ok('① 隐藏后匿名 40400、作者仍可见 visibility=3',
    anon.code === 40400 && owner.code === 0 && owner.data.visibility === 3,
    `anon=${anon.code} owner=${owner.data?.visibility}`);

  let goneFromFeed = false;
  for (let i = 0; i < 15 && !goneFromFeed; i += 1) {
    goneFromFeed = !(await feedIds(null)).includes(noteId);
    if (!goneFromFeed) await sleep(200);
  }
  ok('① 隐藏后从公开 feed 消失', goneFromFeed);

  const sixth = await register('r6');
  const after = await report(sixth.accessToken, 'note', noteId);
  // 隐藏笔记对非作者不可见（404），无法再举报——存在性隐藏的一致语义
  ok('① 隐藏后非作者举报 40400（不可见即不可举报）', after.code === 40400, `code=${after.code}`);
}

// ---------- ② 评论举报（在公开笔记上进行：举报者必须能看到目标） ----------
{
  const publicNoteId = await publish(authorToken, [await uploadReady(authorToken)], 'P18 公开笔记');
  const comment = await json(await fetch(`${B}/notes/${publicNoteId}/comments`, {
    method: 'POST',
    headers: { ...headers(authorToken), 'content-type': 'application/json' },
    body: JSON.stringify({ content: 'P18 评论夹具' }),
  }));
  const commentId = comment.data.id;
  const c1 = await report(reporters[0].accessToken, 'comment', commentId);
  const c2 = await report(reporters[0].accessToken, 'comment', commentId);
  ok('② 评论举报创建与去重', c1.code === 0 && c1.data.created === true
    && c2.code === 0 && c2.data.created === false, JSON.stringify(c1.data) + JSON.stringify(c2.data));
  const ownComment = await report(authorToken, 'comment', commentId);
  ok('② 举报自己的评论 40000', ownComment.code === 40000, `code=${ownComment.code}`);
}

// ---------- ③ 屏蔽：双向过滤 ----------
const u1 = await register('u1');
const u2 = await register('u2');
const u1Note = await publish(u1.accessToken, [await uploadReady(u1.accessToken)], 'P18 u1 的笔记');
const u2Note = await publish(u2.accessToken, [await uploadReady(u2.accessToken)], 'P18 u2 的笔记');

{
  const self = await json(await fetch(`${B}/users/${u1.userId}/block`, {
    method: 'PUT', headers: headers(u1.accessToken),
  }));
  ok('③ 屏蔽自己 40000', self.code === 40000, `code=${self.code}`);

  const put = await json(await fetch(`${B}/users/${u2.userId}/block`, {
    method: 'PUT', headers: headers(u1.accessToken),
  }));
  const putAgain = await json(await fetch(`${B}/users/${u2.userId}/block`, {
    method: 'PUT', headers: headers(u1.accessToken),
  }));
  ok('③ 屏蔽幂等 code=0', put.code === 0 && putAgain.code === 0);

  let ok1 = false;
  let ok2 = false;
  for (let i = 0; i < 15 && !(ok1 && ok2); i += 1) {
    const f1 = await feedIds(u1.accessToken);
    const f2 = await feedIds(u2.accessToken);
    ok1 = !f1.includes(u2Note);
    ok2 = !f2.includes(u1Note);
    if (!(ok1 && ok2)) await sleep(200);
  }
  ok('③ u1 的 feed 不再出现 u2 的笔记', ok1);
  ok('③ u2 的 feed 不再出现 u1 的笔记（双向）', ok2);

  const anon = await feedIds(null);
  ok('③ 匿名 feed 不受屏蔽影响', anon.includes(u1Note) && anon.includes(u2Note));

  const detailByBlocked = await detail(u2.accessToken, u1Note);
  ok('③ 被屏蔽者看屏蔽者笔记详情 40400', detailByBlocked.code === 40400, `code=${detailByBlocked.code}`);

  const gridByBlocked = await grid(u2.accessToken, u1.userId);
  ok('③ 被屏蔽者看屏蔽者主页网格为空', gridByBlocked.code === 0
    && (gridByBlocked.data.list ?? []).length === 0, `n=${gridByBlocked.data?.list?.length}`);

  const search = await json(await fetch(`${B}/search/notes?q=P18&limit=20`, {
    headers: headers(u2.accessToken),
  }));
  const searchIds = (search.data?.list ?? []).map(c => c.id);
  ok('③ 被屏蔽者搜索不到屏蔽者的笔记', !searchIds.includes(u1Note), `ids=${searchIds.slice(0, 6)}`);

  const unblock = await json(await fetch(`${B}/users/${u2.userId}/block`, {
    method: 'DELETE', headers: headers(u1.accessToken),
  }));
  const restored = await detail(u2.accessToken, u1Note);
  ok('③ 取消屏蔽后恢复可见', unblock.code === 0 && restored.code === 0, `restored=${restored.code}`);
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
process.exit(fail.length ? 1 : 0);
