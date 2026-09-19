// Phase 3-3.7 缩略图管线验收：成功消费/DLQ 坏消息两类/匿名可读
import zlib from 'node:zlib';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const MQ = process.env.SCENARY_RABBITMQ_API_BASE_URL ?? 'http://localhost:15672/api';
const envFile = new URL('../../.env', import.meta.url);
const repoRoot = fileURLToPath(new URL('../..', import.meta.url));
const localEnv = Object.fromEntries(readFileSync(envFile, 'utf8').split(/\r?\n/).flatMap(line => {
  const match = line.match(/^\s*([A-Z][A-Z0-9_]*)=(.*)$/);
  return match ? [[match[1], match[2].trim()]] : [];
}));
const mqUser = process.env.RABBITMQ_DEFAULT_USER ?? localEnv.RABBITMQ_DEFAULT_USER;
const mqPass = process.env.RABBITMQ_DEFAULT_PASS ?? localEnv.RABBITMQ_DEFAULT_PASS;
const MQAUTH = mqUser && mqPass ? 'Basic ' + Buffer.from(`${mqUser}:${mqPass}`).toString('base64') : null;
const PASSWORD = `T9${Date.now().toString(36)}a!`;
let pass = 0, fail = 0;
const ok = (n, c, x='') => { c ? pass++ : fail++; console.log(`${c?'PASS':'FAIL'} | ${n}${x?' | '+x:''}`); };

function crc32(buf){crc32.t??=(()=>{let x=[];for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xEDB88320^(c>>>1):c>>>1;x[n]=c>>>0}return x})();let c=0xFFFFFFFF;for(const b of buf)c=crc32.t[(c^b)&0xFF]^(c>>>8);return((c^0xFFFFFFFF)>>>0)}
function chunk(t,d){const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const b=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc32(b));return Buffer.concat([l,b,c])}
function makePng(w,h,[r,g,b]=[64,128,200]){
  const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(w,0);ihdr.writeUInt32BE(h,4);ihdr[8]=8;ihdr[9]=2;
  const stride=1+w*3;
  const rows=[]; for(let y=0;y<h;y++){rows.push(Buffer.from([0])); const px=Buffer.alloc(w*3); px.fill(r);px[1]=g;px[2]=b; rows.push(px);}
  return Buffer.concat([Buffer.from([0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(Buffer.concat(rows))),chunk('IEND',Buffer.alloc(0))]);
}
const compose = args => {
  const result = spawnSync('docker', ['compose', ...args], {cwd:repoRoot, encoding:'utf8'});
  if (result.status !== 0) throw new Error(result.stderr || result.stdout || 'docker compose command failed');
  return result.stdout;
};
let mqMode = process.env.SCENARY_RABBITMQ_MODE ?? 'auto';
const resolveMqMode = async () => {
  if (mqMode !== 'auto') return mqMode;
  if (MQAUTH) {
    try {
      const r = await fetch(MQ+'/overview',{headers:{authorization:MQAUTH}});
      if (r.ok) return mqMode='http';
    } catch {}
  }
  compose(['exec','-T','rabbitmq','rabbitmqctl','version']);
  return mqMode='compose';
};
const mqPublish = async payload => {
  const body = typeof payload==='string' ? payload : JSON.stringify(payload);
  if (await resolveMqMode() === 'http') {
    const r = await fetch(MQ+'/exchanges/%2F/media.event/publish',{method:'POST',headers:{authorization:MQAUTH,'content-type':'application/json'},body:JSON.stringify({properties:{},routing_key:'media.uploaded',payload:body,payload_encoding:'string'})});
    return r.json();
  }
  compose(['exec','-T','rabbitmq','sh','-lc','rabbitmqadmin -u "$RABBITMQ_DEFAULT_USER" -p "$RABBITMQ_DEFAULT_PASS" publish exchange=media.event routing_key=media.uploaded payload="$1" >/dev/null','rabbitmqadmin',body]);
  return {routed:true};
};
const dlqDepth = async () => {
  if (await resolveMqMode() === 'http') {
    const j = await (await fetch(MQ+'/queues/%2F/media.dlq',{headers:{authorization:MQAUTH}})).json();
    return j.messages ?? 0;
  }
  const line = compose(['exec','-T','rabbitmq','rabbitmqctl','list_queues','-q','-p','/','name','messages'])
    .split(/\r?\n/).find(value => /^media\.dlq\s+\d+$/.test(value));
  if (!line) throw new Error('media.dlq was not found.');
  return Number(line.match(/\d+$/)[0]);
};

(async()=>{
  const owner = await(await fetch(B+'/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:`thumb_${Date.now().toString(36).slice(-5)}`,password:PASSWORD})})).json();
  const AUTH={Authorization:'Bearer '+owner.data.accessToken};

  // ① 上传 1000x604 -> 轮询到 status=1
  const fd=new FormData(); fd.append('files',new Blob([makePng(1000,604)],{type:'image/png'}),'big.png');
  const up=await(await fetch(B+'/media/images',{method:'POST',headers:AUTH,body:fd})).json();
  ok('① 上传受理', up.code===0);
  const id=up.data.items[0].mediaId;
  let st=null, tries=0;
  while(tries++<20){
    st=await(await fetch(B+'/media/'+id,{headers:AUTH})).json();
    if(st.data.status===1) break;
    await new Promise(r=>setTimeout(r,400));
  }
  ok('① 6秒内轮询至 status=1', st.data.status===1, '耗时~'+tries*400+'ms');
  ok('① thumbUrl 路径为 thumb/ 对象且带签名（02 §1.5）', /\/scenary-media\/thumb\/\d{6}\/[0-9a-f-]{36}_t\.jpg\?/.test(st.data.thumbUrl||'') && (st.data.thumbUrl||'').includes('X-Amz-Signature='), st.data.thumbUrl);

  // ② 尺寸语义 ≤800 且等比
  ok('② 宽高≤800 且比列≈原图', st.data.width<=800 && st.data.height<=800 &&
     Math.abs(st.data.width/st.data.height - 1000/604) < 0.01,
     `${st.data.width}x${st.data.height}`);

  // ③ 匿名回读缩略图
  {
    const r=await fetch(st.data.thumbUrl);
    const buf=await r.arrayBuffer();
    ok('③ 缩略图匿名 GET 200 jpeg 非空', r.status===200 && r.headers.get('content-type')==='image/jpeg' && buf.byteLength>0, 'bytes='+buf.byteLength);
  }

  // ④⑤ 毒消息两枚进 DLQ
  const before=await dlqDepth();
  await mqPublish('not-json-at-all{{{');                 // 解析失败路径
  await mqPublish({mediaId:987654321});                  // 幽灵行路径（两次重试后 nack）
  let after=before;
  for(let i=0;i<10;i++){ await new Promise(r=>setTimeout(r,500)); after=await dlqDepth(); if(after>=before+2) break; }
  ok('④/⑤ 两枚坏消息均落 media.dlq (+2)', after>=before+2, `depth ${before}->${after}`);

  // ⑥ 正常消息未被毒消息阻塞：再传一张仍能出片
  const fd2=new FormData(); fd2.append('files',new Blob([makePng(300,200,[10,120,90])],{type:'image/png'}),'small.png');
  const up2=await(await fetch(B+'/media/images',{method:'POST',headers:AUTH,body:fd2})).json();
  let st2=null,t=0;
  while(t++<15){ st2=await(await fetch(B+'/media/'+up2.data.items[0].mediaId,{headers:AUTH})).json(); if(st2.data.status===1)break; await new Promise(r=>setTimeout(r,400)); }
  ok('⑥ 坏消息后正常流不受阻', st2.data.status===1 && st2.data.width<=800);

  console.log(`\n==== PASS=${pass} FAIL=${fail} ====`);
  process.exit(fail?1:0);
})();
