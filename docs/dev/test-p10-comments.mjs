// P10 评论与通知黑盒：评论/回复、同笔记约束、限流、软删占位、通知与已读。
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

function sql(query) {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p10-comments', 'scenary', query,
  ]);
}

const json = async response => response.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options));

async function register(username, password, nickname) {
  const body = await request('/auth/register', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password, nickname }),
  });
  if (body.code !== 0) throw new Error(`register failed code=${body.code}`);
  return body.data;
}

async function uploadReady(token) {
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'p10.png');
  const uploaded = await request('/media/images', { method: 'POST', headers: headers(token), body: form });
  if (uploaded.code !== 0) throw new Error(`upload failed code=${uploaded.code}`);
  const mediaId = uploaded.data.items[0].mediaId;
  for (let i = 0; i < 30; i += 1) {
    const state = await request(`/media/${mediaId}`, { headers: headers(token) });
    if (state.data.status === 1) return mediaId;
    if (state.data.status === 2) throw new Error('media processing failed');
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error('media processing timeout');
}

async function publish(token, mediaId, title) {
  const body = await request('/notes', {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ title, content: 'P10 评论通知验收夹具', mediaIds: [mediaId] }),
  });
  if (body.code !== 0) throw new Error(`publish failed code=${body.code}`);
  return body.data.id;
}

async function comment(noteId, token, content, parentId = null) {
  return request(`/notes/${noteId}/comments`, {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ content, parentId }),
  });
}

try {
  const suffix = Date.now().toString(36).slice(-6);
  const password = `T9${Date.now().toString(36)}a!`;
  const author = await register(`p10a_${suffix}`, password, 'P10作者');
  const reader = await register(`p10b_${suffix}`, password, 'P10读者');
  const other = await register(`p10c_${suffix}`, password, 'P10回复者');
  const authorHeaders = headers(author.accessToken);
  const readerHeaders = headers(reader.accessToken);
  const otherHeaders = headers(other.accessToken);
  const noteId = await publish(author.accessToken, await uploadReady(author.accessToken), 'P10公开笔记');
  const otherNoteId = await publish(author.accessToken, await uploadReady(author.accessToken), 'P10另一笔记');

  const anonymous = await request(`/notes/${noteId}/comments?limit=2`);
  ok('匿名可读取公开笔记评论', anonymous.code === 0 && anonymous.data.list.length === 0);

  const root = await comment(noteId, reader.accessToken, '第一条评论');
  ok('创建一级评论并返回 mine', root.code === 0 && root.data.status === 1 && root.data.mine === true && root.data.parentId === null);
  const reply = await comment(noteId, other.accessToken, '这是回复', root.data.id);
  ok('创建同笔记回复并返回 parentId', reply.code === 0 && reply.data.parentId === root.data.id);

  const crossNote = await comment(otherNoteId, other.accessToken, '跨笔记回复应拒绝', root.data.id);
  ok('跨笔记 parentId 被服务端拒绝', crossNote.code === 40000);
  const html = await comment(noteId, reader.accessToken, '<script>alert(1)</script>');
  ok('HTML 评论被拒绝', html.code === 40000);
  const sensitive = await comment(noteId, reader.accessToken, '这是诈骗信息');
  ok('敏感词评论被拒绝且不回显词项', sensitive.code === 40000 && !String(sensitive.message ?? '').includes('诈骗'));
  const tooLong = await comment(noteId, reader.accessToken, '一'.repeat(501));
  ok('一级评论超过 500 字被拒绝', tooLong.code === 40000);

  let successful = 1;
  for (let i = 0; i < 19; i += 1) {
    const body = await comment(noteId, reader.accessToken, `限流测试 ${i}`);
    if (body.code === 0) successful += 1;
  }
  const rateLimited = await comment(noteId, reader.accessToken, '第 21 条应限流');
  ok('评论 20 条/分钟后返回 42001', successful === 20 && rateLimited.code === 42001, `success=${successful}`);

  const firstPage = await request(`/notes/${noteId}/comments?limit=2`);
  const secondPage = await request(`/notes/${noteId}/comments?limit=20&cursor=${firstPage.data.nextCursor}`);
  const firstIds = firstPage.data.list.map(item => item.id);
  const secondIds = secondPage.data.list.map(item => item.id);
  ok('评论游标按 id ASC 且分页不重叠', firstPage.code === 0 && firstPage.data.hasMore &&
    firstIds.every((id, index) => index === 0 || id > firstIds[index - 1]) &&
    !secondIds.some(id => firstIds.includes(id)));

  const forbiddenDelete = await request(`/comments/${root.data.id}`, {
    method: 'DELETE', headers: otherHeaders,
  });
  ok('非作者不能删除评论', forbiddenDelete.code === 40300);
  const deleteRoot = await request(`/comments/${root.data.id}`, {
    method: 'DELETE', headers: readerHeaders,
  });
  const repeatDelete = await request(`/comments/${root.data.id}`, {
    method: 'DELETE', headers: readerHeaders,
  });
  const afterDelete = await request(`/notes/${noteId}/comments?limit=20`);
  const deletedItem = afterDelete.data.list.find(item => item.id === root.data.id);
  ok('作者软删幂等且列表保留已删除占位', deleteRoot.code === 0 && repeatDelete.code === 0 &&
    deletedItem?.status === 2 && deletedItem.content === '该评论已删除' && deletedItem.canDelete === false);

  const authorNotifications = await request('/notifications?limit=20', { headers: authorHeaders });
  const hasComment = authorNotifications.data.list.some(item => item.type === 'COMMENT' && item.noteId === noteId);
  const readerNotifications = await request('/notifications?limit=20', { headers: readerHeaders });
  const hasReply = readerNotifications.data.list.some(item => item.type === 'REPLY' && item.commentId === reply.data.id);
  ok('一级评论通知发送给笔记作者', authorNotifications.code === 0 && hasComment && authorNotifications.data.unreadCount >= 1);
  ok('回复通知发送给被回复评论作者', readerNotifications.code === 0 && hasReply && readerNotifications.data.unreadCount >= 1);

  const like = await request(`/notes/${noteId}/like`, { method: 'PUT', headers: otherHeaders });
  const follow = await request(`/users/${author.userId}/follow`, { method: 'PUT', headers: otherHeaders });
  const afterSocial = await request('/notifications?limit=20', { headers: authorHeaders });
  ok('点赞与关注统一产生通知', like.code === 0 && follow.code === 0 &&
    afterSocial.data.list.some(item => item.type === 'LIKE' && item.noteId === noteId) &&
    afterSocial.data.list.some(item => item.type === 'FOLLOW'));

  const targetNotification = afterSocial.data.list.find(item => item.type === 'COMMENT' && item.noteId === noteId);
  const readOne = await request('/notifications/read', {
    method: 'POST', headers: { ...authorHeaders, 'content-type': 'application/json' },
    body: JSON.stringify({ ids: [targetNotification.id] }),
  });
  const afterRead = await request('/notifications?limit=20', { headers: authorHeaders });
  ok('指定通知可标记已读且未读数下降', readOne.code === 0 &&
    afterRead.data.list.find(item => item.id === targetNotification.id)?.readAt !== null &&
    afterRead.data.unreadCount < afterSocial.data.unreadCount);
  const readAll = await request('/notifications/read', {
    method: 'POST', headers: { ...readerHeaders, 'content-type': 'application/json' },
    body: JSON.stringify({ ids: [] }),
  });
  const readerAfterReadAll = await request('/notifications?limit=1', { headers: readerHeaders });
  ok('空 ids 批量标记当前用户全部已读', readAll.code === 0 && readerAfterReadAll.data.unreadCount === 0);

  const privateMedia = await uploadReady(author.accessToken);
  const privateNote = await request('/notes', {
    method: 'POST', headers: { ...authorHeaders, 'content-type': 'application/json' },
    body: JSON.stringify({ title: 'P10私密笔记', mediaIds: [privateMedia], visibility: 0 }),
  });
  const privateComments = await request(`/notes/${privateNote.data.id}/comments`);
  const privateAsAuthor = await request(`/notes/${privateNote.data.id}/comments`, { headers: authorHeaders });
  ok('私密笔记评论遵循详情可见性', privateComments.code === 40400 && privateAsAuthor.code === 0);

  const v5 = sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='scenary' AND table_name IN ('comments','notifications')");
  const fk = sql("SELECT COUNT(DISTINCT CONSTRAINT_NAME) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA='scenary' AND CONSTRAINT_NAME='fk_comment_parent_note'");
  ok('V5 表与同笔记复合外键已落库', v5 === '2' && fk === '1', `tables=${v5}; fk=${fk}`);
} catch (error) {
  ok('P10 黑盒执行完成', false, error.message);
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
process.exit(fail.length ? 1 : 0);
