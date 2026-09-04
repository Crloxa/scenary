// P11 搜索与发现黑盒：字段覆盖、可见性、排序、opaque cursor、转义和查询基线。
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';

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

function sql(query) {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p11-search', 'scenary', query,
  ]);
}

const json = async response => response.json();
const headers = token => ({ Authorization: `Bearer ${token}` });
const request = async (path, options = {}) => json(await fetch(`${B}${path}`, options));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

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
  form.append('files', new Blob([image], { type: 'image/png' }), 'p11.png');
  const uploaded = await request('/media/images', { method: 'POST', headers: headers(token), body: form });
  if (uploaded.code !== 0) throw new Error(`upload failed code=${uploaded.code}`);
  const mediaId = uploaded.data.items[0].mediaId;
  for (let i = 0; i < 30; i += 1) {
    const state = await request(`/media/${mediaId}`, { headers: headers(token) });
    if (state.data.status === 1) return mediaId;
    if (state.data.status === 2) throw new Error('media processing failed');
    await sleep(250);
  }
  throw new Error('media processing timeout');
}

async function publish(token, title, content, placeName, visibility = 1) {
  const body = await request('/notes', {
    method: 'POST',
    headers: { ...headers(token), 'content-type': 'application/json' },
    body: JSON.stringify({
      title, content, placeName,
      mediaIds: [await uploadReady(token)], visibility,
    }),
  });
  if (body.code !== 0) throw new Error(`publish failed code=${body.code}`);
  return body.data.id;
}

async function search(q, options = {}) {
  const params = new URLSearchParams({ q, limit: String(options.limit ?? 20) });
  if (options.cursor) params.set('cursor', options.cursor);
  if (options.sort) params.set('sort', options.sort);
  return request(`/search/notes?${params}`);
}

let disabledUserId = null;
try {
  const suffix = Date.now().toString(36).slice(-6);
  const password = `T9${Date.now().toString(36)}a!`;
  const author = await register(`p11a_${suffix}`, password, `P11作者${suffix}`);
  const disabled = await register(`p11b_${suffix}`, password, `P11禁用${suffix}`);
  disabledUserId = disabled.userId;

  const publicIds = [];
  publicIds.push(await publish(author.accessToken, `P11X${suffix}-云海`, 'A blue SKY above the cloud sea', '云南·云海'));
  publicIds.push(await publish(author.accessToken, `P11X${suffix}-古堡`, 'Moon over the old stone wall', 'Old Wall'));
  publicIds.push(await publish(author.accessToken, `P11X${suffix}-湖泊`, 'Quiet water and mountain light', 'Blue Lake'));
  const privateId = await publish(author.accessToken, `P11X${suffix}-私密`, 'private cloud sea', 'Hidden', 0);
  const deletedId = await publish(author.accessToken, `P11X${suffix}-删除`, 'deleted cloud sea', 'Gone');
  const disabledId = await publish(disabled.accessToken, `P11X${suffix}-禁用`, 'disabled cloud sea', 'Blocked');
  await request(`/notes/${deletedId}`, { method: 'DELETE', headers: headers(author.accessToken) });
  sql(`UPDATE users SET status=0 WHERE id=${disabledUserId}`);

  const titleSearch = await search(`  p11x${suffix}  `, { limit: 1, sort: 'recent' });
  ok('中文/大小写/首尾空格查询命中公开结果', titleSearch.code === 0 && titleSearch.data.list.length === 1 &&
    titleSearch.data.list[0].id === publicIds[2]);
  ok('搜索卡片包含纯文本 highlight 且无 HTML', titleSearch.data.list[0]?.highlight &&
    Object.values(titleSearch.data.list[0].highlight).some(Boolean) &&
    !JSON.stringify(titleSearch.data.list[0].highlight).includes('<'));

  const contentSearch = await search('cloud', { sort: 'relevance' });
  ok('英文正文搜索大小写不敏感', contentSearch.code === 0 &&
    contentSearch.data.list.some(item => item.id === publicIds[0]));
  const placeSearch = await search('云南', { sort: 'relevance' });
  ok('地点字段可搜索', placeSearch.code === 0 && placeSearch.data.list.some(item => item.id === publicIds[0]));
  const authorSearch = await search(`作者${suffix}`, { sort: 'relevance' });
  ok('作者昵称字段可搜索', authorSearch.code === 0 && authorSearch.data.list.some(item => item.id === publicIds[0]));

  const visible = await search(`P11X${suffix}`, { limit: 1, sort: 'recent' });
  if (visible.code !== 0 || !visible.data) throw new Error(`recent search failed code=${visible.code}`);
  const collected = [...visible.data.list];
  let cursor = visible.data.nextCursor;
  while (visible.code === 0 && visible.data.hasMore && cursor) {
    const page = await search(`P11X${suffix}`, { limit: 1, sort: 'recent', cursor });
    if (page.code !== 0 || !page.data) throw new Error(`cursor search failed code=${page.code}`);
    collected.push(...page.data.list);
    cursor = page.data.nextCursor;
    if (collected.length > 10) break;
  }
  const ids = collected.map(item => item.id);
  ok('recent opaque cursor 翻页无重复且过滤私密/删除/禁用内容',
    new Set(ids).size === ids.length && publicIds.every(id => ids.includes(id)) &&
    ![privateId, deletedId, disabledId].some(id => ids.includes(id)) && ids.length === 3,
    `resultCount=${ids.length}`);

  const relevance = await search(`p11x${suffix}`, { limit: 20, sort: 'relevance' });
  if (relevance.code !== 0 || !relevance.data) throw new Error(`relevance search failed code=${relevance.code}`);
  ok('relevance 排序可用且返回同一可见集合', relevance.code === 0 &&
    relevance.data.list.length === 3 && relevance.data.list.every(item => publicIds.includes(item.id)));

  const special = await search(`P11X${suffix}%`, { sort: 'recent' });
  ok('百分号按字面转义而非 LIKE 通配符', special.code === 0 && special.data.list.length === 0);
  const blank = await search(' ');
  ok('空查询返回 40000', blank.code === 40000);
  const short = await search('x');
  ok('单字符查询返回 40000', short.code === 40000);
  const long = await search('a'.repeat(65));
  ok('超过 64 字符查询返回 40000', long.code === 40000);
  const badSort = await search('云海', { sort: 'popular' });
  ok('非法 sort 返回 40000', badSort.code === 40000);

  const v6 = sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='6' AND success=1");
  const indexes = sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='scenary' AND index_name IN ('idx_search_visibility_created_id','idx_search_visibility_user_id','idx_search_status_id','idx_media_note_order')");
  ok('V6 已应用且四个搜索读路径索引已落库', v6 === '1' && indexes === '4', `v6=${v6}; indexes=${indexes}`);

  const timings = [];
  for (let i = 0; i < 20; i += 1) {
    const start = performance.now();
    const body = await search(`p11x${suffix}`, { limit: 10, sort: i % 2 ? 'recent' : 'relevance' });
    timings.push(performance.now() - start);
    if (body.code !== 0) throw new Error(`baseline search failed code=${body.code}`);
  }
  timings.sort((a, b) => a - b);
  const p95 = timings[Math.ceil(timings.length * 0.95) - 1];
  console.log(`BASELINE | samples=${timings.length} p95=${p95.toFixed(2)}ms`);
} catch (error) {
  ok('P11 黑盒执行完成', false, error.message);
} finally {
  if (disabledUserId !== null) {
    try { sql(`UPDATE users SET status=1 WHERE id=${disabledUserId}`); } catch {}
  }
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====\n`);
process.exit(fail.length ? 1 : 0);
