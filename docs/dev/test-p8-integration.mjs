// P8 真实 Compose 集成矩阵：Flyway/MySQL、Redis 吊销、RabbitMQ 最终失败/DLQ、Feed 并发缓存。
// MinIO 写入失败后的对象补偿由 MediaServiceTest 覆盖；发布幂等由 test-e2e38.mjs 覆盖。
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8081/api/v1';
const pass = [];
const fail = [];
const ok = (name, condition, detail = '') => {
  (condition ? pass : fail).push(name);
  console.log(`${condition ? 'PASS' : 'FAIL'} | ${name}${detail ? ` | ${detail}` : ''}`);
};

function compose(args) {
  const result = spawnSync('docker', ['compose', ...args], {
    cwd: fileURLToPath(new URL('../..', import.meta.url)),
    encoding: 'utf8',
  });
  if (result.status !== 0) throw new Error('docker compose command failed');
  return result.stdout.trim();
}

function sql(query, database = 'scenary') {
  return compose([
    'exec', '-T', 'mysql', 'sh', '-lc',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NBe "$2" "$1"',
    'p8-integration', database, query,
  ]);
}

function queueDepth(queue) {
  const output = compose(['exec', '-T', 'rabbitmq', 'rabbitmqctl', 'list_queues', '-q', '-p', '/', 'name', 'messages']);
  const line = output.split(/\r?\n/).find(value => new RegExp(`^${queue}\\s+\\d+$`).test(value));
  if (!line) throw new Error(`queue ${queue} not found`);
  return Number(line.match(/\d+$/)[0]);
}

function publishUploaded(payload) {
  compose([
    'exec', '-T', 'rabbitmq', 'sh', '-lc',
    'rabbitmqadmin -u "$RABBITMQ_DEFAULT_USER" -p "$RABBITMQ_DEFAULT_PASS" publish exchange=media.event routing_key=media.uploaded payload="$1" >/dev/null',
    'p8-integration', payload,
  ]);
}

async function json(response) {
  return response.json();
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let failureMediaId = null;

try {
  const flyway = sql("SELECT CONCAT(version, ':', success) FROM flyway_schema_history WHERE version IN ('2', '3') ORDER BY installed_rank");
  ok('MySQL/Flyway V2、V3 已成功应用', flyway.split(/\r?\n/).every(row => row.endsWith(':1')), flyway);

  const constraints = sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='media' AND index_name='uk_media_note_order'")
    .trim();
  const checks = sql("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='media' AND constraint_name IN ('chk_media_status','chk_media_order_no','chk_media_failure_pair')")
    .trim();
  ok('数据库约束存在（media 唯一键 + CHECK）', constraints === '1' && checks === '3', `unique=${constraints} checks=${checks}`);

  const suffix = Date.now().toString(36).slice(-6);
  const password = `T9${Date.now().toString(36)}a!`;
  const registered = await json(await fetch(`${B}/auth/register`, {
    method: 'POST', headers: {'content-type': 'application/json'},
    body: JSON.stringify({username: `p8i_${suffix}`, password}),
  }));
  ok('Redis 吊销矩阵准备用户', registered.code === 0);
  const accessToken = registered.data?.accessToken;
  await fetch(`${B}/auth/logout`, {method: 'POST', headers: {Authorization: `Bearer ${accessToken}`, 'content-type': 'application/json'}, body: '{}'});
  const revoked = await fetch(`${B}/users/me`, {headers: {Authorization: `Bearer ${accessToken}`}});
  const revokedBody = await json(revoked);
  ok('Redis 吊销后 access 立即失效', revoked.status === 401 && revokedBody.code === 40101, `code=${revokedBody.code}`);

  const badObjectKey = `orig/202609/p8-failure-${Date.now()}.jpg`;
  const inserted = sql(`INSERT INTO media (user_id,note_id,order_no,bucket,object_key,url,mime,size_bytes,status,publish_status,publish_attempts) VALUES (${registered.data.userId},NULL,1,'scenary-media','${badObjectKey}','http://unused/${badObjectKey}','image/jpeg',1,0,1,0); SELECT LAST_INSERT_ID()`);
  const mediaId = Number(inserted.split(/\r?\n/).at(-1));
  failureMediaId = mediaId;
  const beforeDlq = queueDepth('media.dlq');
  publishUploaded(JSON.stringify({mediaId}));
  let settled = '';
  let afterDlq = beforeDlq;
  for (let i = 0; i < 20; i++) {
    await sleep(500);
    settled = sql(`SELECT CONCAT(status,'|',IF(failure_reason IS NULL,0,1),'|',IF(failed_at IS NULL,0,1)) FROM media WHERE id=${mediaId}`);
    afterDlq = queueDepth('media.dlq');
    if (settled === '2|1|1' && afterDlq >= beforeDlq + 1) break;
  }
  ok('RabbitMQ 消费最终失败写 status=2 并进 DLQ', settled === '2|1|1' && afterDlq >= beforeDlq + 1, `state=${settled} dlq=${beforeDlq}->${afterDlq}`);
  sql(`DELETE FROM media WHERE id=${mediaId}`);
  failureMediaId = null;

  const feedResponses = await Promise.all(Array.from({length: 8}, () => fetch(`${B}/feed`)));
  const feedBodies = await Promise.all(feedResponses.map(json));
  const version = compose(['exec', '-T', 'redis', 'redis-cli', 'GET', 'feed:first:v1:version']);
  ok('Feed 并发读取均成功且存在版本化缓存版本', feedBodies.every(body => body.code === 0) && /^\d+$/.test(version), `requests=${feedBodies.length} version=${version}`);
} catch (error) {
  ok('P8 Compose 集成矩阵执行完成', false, error.message);
} finally {
  if (failureMediaId !== null) {
    try { sql(`DELETE FROM media WHERE id=${failureMediaId}`); } catch {}
  }
}

console.log(`\n==== PASS=${pass.length} FAIL=${fail.length} ====`);
process.exit(fail.length ? 1 : 0);
