// P9 社交最小闭环：幂等关系、匿名/登录视图、并发唯一性、可见性和收藏游标。
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
  const result = spawnSync('docker', ['compose', ...args], {
    cwd: ROOT,
    encoding: 'utf8',
  });
  if (result.status !== 0) throw new Error('docker compose command failed');
  return result.stdout.trim();
}

function sql(query, database = 'scenary') {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p9-social', database, query,
  ]);
}

const json = async response => response.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function register(username, password, nickname) {
  const body = await json(await fetch(`${B}/auth/register`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname }),
  }));
  if (body.code !== 0) throw new Error(`register failed code=${body.code}`);
  return body.data;
}

async function uploadReady(token) {
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'p9.png');
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

async function publish(token, mediaId, title, visibility = 1) {
  const body = await json(await fetch(`${B}/notes`, {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ title, content: 'P9 社交验收夹具', mediaIds: [mediaId], visibility }),
  }));
  if (body.code !== 0) throw new Error(`publish failed code=${body.code}`);
  return body.data.id;
}

async function note(token, noteId) {
  return json(await fetch(`${B}/notes/${noteId}`, token ? { headers: headers(token) } : {}));
}

async function action(path, method, token) {
  return json(await fetch(`${B}${path}`, { method, headers: headers(token) }));
}

async function feedUntil(feedHeaders, noteId) {
  let latest;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    latest = await json(await fetch(`${B}/feed?limit=20`, { headers: feedHeaders }));
    if (latest.data?.list?.some(card => card.id === noteId)) return latest;
    await sleep(50);
  }
  return latest;
}

let disabledUserId = null;
try {
  const suffix = Date.now().toString(36).slice(-6);
  const password = `T9${Date.now().toString(36)}a!`;
  const author = await register(`p9a_${suffix}`, password, 'P9作者');
  const viewer = await register(`p9b_${suffix}`, password, 'P9读者');
  const disabled = await register(`p9c_${suffix}`, password, 'P9禁用');
  disabledUserId = disabled.userId;
  const authorHeaders = headers(author.accessToken);
  const viewerHeaders = headers(viewer.accessToken);
  const media = await uploadReady(author.accessToken);
  const publicNoteId = await publish(author.accessToken, media, 'P9公开风景');
  const privateMedia = await uploadReady(author.accessToken);
  const privateNoteId = await publish(author.accessToken, privateMedia, 'P9私密风景', 0);

  const anonymous = await note(null, publicNoteId);
  ok('匿名详情返回公开计数且不泄露个人状态', anonymous.code === 0 &&
    anonymous.data.social.liked === false && anonymous.data.social.bookmarked === false &&
    anonymous.data.social.likeCount === 0);

  const likeResults = await Promise.all(Array.from({ length: 20 }, () =>
    action(`/notes/${publicNoteId}/like`, 'PUT', viewer.accessToken)));
  const liked = await note(viewer.accessToken, publicNoteId);
  const likeRows = sql(`SELECT COUNT(*) FROM note_likes WHERE note_id=${publicNoteId} AND user_id=${viewer.userId}`);
  ok('并发 20 次点赞最终只有一条关系且计数为 1',
    likeResults.every(body => body.code === 0) && liked.data.social.liked === true &&
    liked.data.social.likeCount === 1 && likeRows === '1', likeRows);

  const unlikeResults = await Promise.all(Array.from({ length: 20 }, () =>
    action(`/notes/${publicNoteId}/like`, 'DELETE', viewer.accessToken)));
  const unliked = await note(viewer.accessToken, publicNoteId);
  ok('并发重复取消点赞幂等且计数归零', unlikeResults.every(body => body.code === 0) &&
    unliked.data.social.liked === false && unliked.data.social.likeCount === 0);

  const bookmarks = await Promise.all([
    action(`/notes/${publicNoteId}/bookmark`, 'PUT', viewer.accessToken),
    action(`/notes/${publicNoteId}/bookmark`, 'PUT', viewer.accessToken),
  ]);
  const marked = await note(viewer.accessToken, publicNoteId);
  const bookmarkRows = sql(`SELECT COUNT(*) FROM note_bookmarks WHERE note_id=${publicNoteId} AND user_id=${viewer.userId}`);
  ok('重复收藏只建立一条关系', bookmarks.every(body => body.code === 0) &&
    marked.data.social.bookmarked === true && marked.data.social.bookmarkCount === 1 && bookmarkRows === '1');

  const bookmarkPage = await json(await fetch(`${B}/users/me/bookmarks?limit=1`, { headers: viewerHeaders }));
  ok('我的收藏游标列表返回当前公开笔记', bookmarkPage.code === 0 &&
    bookmarkPage.data.list.some(item => item.id === publicNoteId) &&
    bookmarkPage.data.list[0].social.bookmarked === true);
  const unbookmark = await action(`/notes/${publicNoteId}/bookmark`, 'DELETE', viewer.accessToken);
  const bookmarkAfter = await json(await fetch(`${B}/users/me/bookmarks?limit=20`, { headers: viewerHeaders }));
  ok('取消收藏幂等且列表不再出现', unbookmark.code === 0 &&
    !bookmarkAfter.data.list.some(item => item.id === publicNoteId));

  const followResults = await Promise.all(Array.from({ length: 20 }, () =>
    action(`/users/${author.userId}/follow`, 'PUT', viewer.accessToken)));
  const profile = await json(await fetch(`${B}/users/${author.userId}`, { headers: viewerHeaders }));
  const followRows = sql(`SELECT COUNT(*) FROM follows WHERE follower_id=${viewer.userId} AND following_id=${author.userId}`);
  ok('并发 20 次关注最终只有一条关系且计数为 1', followResults.every(body => body.code === 0) &&
    profile.data.social.following === true && profile.data.social.followerCount === 1 && followRows === '1');
  const unfollow = await action(`/users/${author.userId}/follow`, 'DELETE', viewer.accessToken);
  const profileAfter = await json(await fetch(`${B}/users/${author.userId}`, { headers: viewerHeaders }));
  ok('取消关注幂等且主页状态刷新一致', unfollow.code === 0 && profileAfter.data.social.following === false &&
    profileAfter.data.social.followerCount === 0);

  const selfFollow = await action(`/users/${viewer.userId}/follow`, 'PUT', viewer.accessToken);
  ok('不能关注自己', selfFollow.code === 40000);

  const privateLike = await action(`/notes/${privateNoteId}/like`, 'PUT', viewer.accessToken);
  ok('私密笔记不能点赞并隐藏存在性', privateLike.code === 40400);
  const disabledRowsBefore = sql(`UPDATE users SET status=0 WHERE id=${disabledUserId}; SELECT status FROM users WHERE id=${disabledUserId}`);
  const disabledFollow = await action(`/users/${disabledUserId}/follow`, 'PUT', viewer.accessToken);
  ok('被禁用用户不能建立关注关系', disabledFollow.code === 40400 && disabledRowsBefore.endsWith('0'));
  sql(`UPDATE users SET status=1 WHERE id=${disabledUserId}`);

  const feed = await feedUntil(viewerHeaders, publicNoteId);
  const feedTarget = feed.data?.list?.find(card => card.id === publicNoteId);
  ok('登录 Feed 包含统一社交状态结构', feed.code === 0 &&
    feedTarget?.social && feedTarget.social.liked === false && feedTarget.social.bookmarked === false,
    `target=${publicNoteId}; ids=${feed.data?.list?.map(card => card.id).join(',')}; social=${JSON.stringify(feedTarget?.social ?? null)}`);

  const deleted = await action(`/notes/${publicNoteId}`, 'DELETE', author.accessToken);
  const likeDeleted = await action(`/notes/${publicNoteId}/like`, 'PUT', viewer.accessToken);
  ok('删除后不能新增社交关系', deleted.code === 0 && likeDeleted.code === 40400);
} catch (error) {
  ok('P9 黑盒执行完成', false, error.message);
} finally {
  if (disabledUserId !== null) {
    try { sql(`UPDATE users SET status=1 WHERE id=${disabledUserId}`); } catch {}
  }
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
process.exit(fail.length ? 1 : 0);
