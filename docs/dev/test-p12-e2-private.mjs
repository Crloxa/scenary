// P12-E2 私有桶与短时签名黑盒：签名 URL 匿名可读、桶私有后直链/伪造/存在性探测全部 403、
// feed/详情出响应即签名、持久化列只存 key。可重复执行（随机用户名）。
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
  if (result.status !== 0) throw new Error(`docker compose failed: ${args.join(' ')}`);
  return result.stdout.trim();
}

function sql(query) {
  return compose(['exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p12-e2', 'scenary', query]);
}

const rnd = Math.random().toString(36).slice(2, 8);
const headers = token => ({ Authorization: `Bearer ${token}` });
const json = async response => response.json();
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options));

async function register(username) {
  const body = await request('/auth/register', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password: 'Passw0rd!234', nickname: username }),
  });
  if (body.code !== 0) throw new Error(`register failed code=${body.code}`);
  return body.data;
}

async function uploadImage(token) {
  const image = readFileSync(`${ROOT}/docs/dev/fixtures/a.png`);
  const form = new FormData();
  form.append('files', new Blob([image], { type: 'image/png' }), 'e2.png');
  const uploaded = await request('/media/images', { method: 'POST', headers: headers(token), body: form });
  if (uploaded.code !== 0) throw new Error(`upload failed code=${uploaded.code}`);
  const mediaId = uploaded.data.items[0].mediaId;
  for (let i = 0; i < 30; i++) {
    await new Promise(r => setTimeout(r, 1000));
    const q = await request(`/media/${mediaId}`, { headers: headers(token) });
    if (q.data?.thumbUrl) return { mediaId, item: q.data };
  }
  throw new Error('thumbnail not ready in 30s');
}

async function createNote(token, mediaId, visibility, title) {
  const body = await request('/notes', {
    method: 'POST', headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({ title, content: 'e2', mediaIds: [mediaId], visibility }),
  });
  if (body.code !== 0) throw new Error(`note create failed code=${body.code}`);
  return body.data;
}

const SIGNED = url => url && url.includes('X-Amz-Signature=');

// ---- 准备夹具：用户 A（私密笔记 + 公开笔记，媒体各自独立）----
const user = await register(`e2_${rnd}`);
const { mediaId: privMedia } = await uploadImage(user.accessToken);
const { mediaId: pubMedia } = await uploadImage(user.accessToken);
const privateNote = await createNote(user.accessToken, privMedia, 0, 'E2 私密夹具');
const publicNote = await createNote(user.accessToken, pubMedia, 1, 'E2 公开夹具');

// ---- ① 持久化列只存 key（回填后全库无 http 直链）----
{
  const urls = sql(`SELECT
    (SELECT COUNT(*) FROM media WHERE url LIKE 'http%' OR thumb_url LIKE 'http%')
    + (SELECT COUNT(*) FROM notes WHERE cover_url LIKE 'http%')
    + (SELECT COUNT(*) FROM users WHERE avatar_url LIKE 'http%')`).trim();
  ok('① 持久化列无 http 直链（key 语义）', urls === '0', `残留=${urls}`);
}

// ---- ② 私密笔记：owner 拿到的详情 URL 是短时签名，匿名凭其可读（签名即授权）----
{
  const detail = await request(`/notes/${privateNote.id}`, { headers: headers(user.accessToken) });
  const image = detail.data.images[0];
  const res = await fetch(image.url);
  const isSigned = SIGNED(image.url) && SIGNED(image.thumbUrl);
  ok('② 详情返回短时签名 URL', isSigned, `url 含 X-Amz-Signature=${isSigned}`);
  ok('② 签名 URL 匿名可读（200）', res.status === 200, `status=${res.status} type=${res.headers.get('content-type')}`);
}

// ---- ③ 审计漏洞复跑：剥离签名的桶内直链匿名访问 → 403（此前为 200）----
{
  const detail = await request(`/notes/${privateNote.id}`, { headers: headers(user.accessToken) });
  const rawUrl = detail.data.images[0].url.split('?')[0];
  const res = await fetch(rawUrl);
  ok('③ 无签名直链匿名访问 403', res.status === 403, `status=${res.status}`);
}

// ---- ④ 篡改签名 → 403 ----
{
  const detail = await request(`/notes/${privateNote.id}`, { headers: headers(user.accessToken) });
  const tampered = detail.data.images[0].url.replace(/X-Amz-Signature=[^&]+/, 'X-Amz-Signature=tampered');
  const res = await fetch(tampered);
  ok('④ 篡改签名拒绝 403', res.status === 403, `status=${res.status}`);
}

// ---- ⑤ 匿名可访问面收敛：不存在对象匿名 GET → 403（封死存在性探测，此前 404）----
{
  const res = await fetch(`http://localhost:8081/minio/scenary-media/orig/1970/no-such-${rnd}.png`);
  ok('⑤ 匿名探测不存在对象 403', res.status === 403, `status=${res.status}`);
}

// ---- ⑥ 公开笔记匿名视角：feed 封面与详情图片出响应即签名且可读，Range 生效 ----
{
  const feed = await request('/feed?page=1&limit=10');
  const card = feed.data.list.find(c => c.id === publicNote.id);
  const feedRes = card ? await fetch(card.coverUrl) : null;
  ok('⑥ feed 封面签名且匿名可读', card && SIGNED(card.coverUrl) && feedRes?.status === 200,
    `命中=${Boolean(card)} status=${feedRes?.status}`);

  const detail = await request(`/notes/${publicNote.id}`);
  const image = detail.data.images[0];
  const range = await fetch(image.url, { headers: { Range: 'bytes=0-100' } });
  ok('⑥ 详情图片签名 + Range 206', SIGNED(image.url) && range.status === 206,
    `status=${range.status}`);
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
if (fail.length) {
  console.log(`失败项：\n- ${fail.join('\n- ')}`);
  process.exit(1);
}
