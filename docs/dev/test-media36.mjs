// Phase 3-3.6 上传管线验收：真实 PNG 生成(zlib 手工编码) + 魔数欺骗 + 越权/删除闭环
import zlib from 'node:zlib';
const B = 'http://localhost:8080/api/v1';
let pass = 0, fail = 0;
const ok = (n, c, x='') => { c ? pass++ : fail++; console.log(`${c?'PASS':'FAIL'} | ${n}${x?' | '+x:''}`); };

// ---- 最小合法 PNG（1px 纯色），可被真实解码器读取（供 3.7 消费者复用）----
function crc32(buf) {
  let table = crc32.t ||= (()=>{let t=[];for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xEDB88320^(c>>>1):c>>>1;t[n]=c>>>0}return t})();
  let c = 0xFFFFFFFF;
  for (const b of buf) c = table[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function makePng(rgb = [64, 128, 200]) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(1,0); ihdr.writeUInt32BE(1,4); ihdr[8]=8; ihdr[9]=2;
  const raw = Buffer.concat([Buffer.from([0]), Buffer.from(rgb)]);
  return Buffer.concat([
    Buffer.from([0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))
  ]);
}
const png = makePng();

const login = await (await fetch(B+'/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:'hill_walker',password:'Str0ngPass!'})})).json();
ok('准备: 登录取 token', login.code===0);
const AUTH = {Authorization:'Bearer '+login.data.accessToken};

// ① 未携带 token 直接上传 -> 40100
{
  const fd = new FormData(); fd.append('files', new Blob([png],{type:'image/png'}), 'a.png');
  const r = await fetch(B+'/media/images',{method:'POST',body:fd});
  const j = await r.json();
  ok('① 无token上传 -> 40100', r.status===401 && j.code===40100);
}

// ② 正常上传两张 PNG
{
  const fd = new FormData();
  fd.append('files', new Blob([png],{type:'image/png'}), 'a.png');
  fd.append('files', new Blob([makePng([200,80,60])],{type:'image/png'}), 'b.png');
  const r = await fetch(B+'/media/images',{method:'POST',headers:AUTH,body:fd});
  const j = await r.json();
  ok('② 双图上传 code=0 items=2', j.code===0 && j.data.items.length===2);
  ok('② 状态=0 thumbUrl=null width=null',
     j.data.items.every(i=>i.status===0&&i.thumbUrl===null&&i.width===null));
  ok('② URL 形如 publicHost/bucket/orig/', /\/scenary-media\/orig\/\d{6}\/[0-9a-f-]{36}\.png$/.test(j.data.items[0].url), j.data.items[0].url);
  globalThis.ids = j.data.items.map(i=>i.mediaId);
  globalThis.urls = j.data.items.map(i=>i.url);
}

// ③ 匿名直读对象 URL（bucket download 策略）
{
  const r = await fetch(globalThis.urls[0]);
  ok('③ 对象 URL 可匿名 GET 且 content-type=image/png', r.status===200 && r.headers.get('content-type')==='image/png');
}

// ④ 魔数欺骗：文本内容伪装 .jpg
{
  const fd = new FormData(); fd.append('files', new Blob([Buffer.from('this is not an image at all')],{type:'image/jpeg'}), 'fake.jpg');
  const r = await fetch(B+'/media/images',{method:'POST',headers:AUTH,body:fd});
  const j = await r.json();
  ok('④ 内容伪装 -> 40000', r.status===400 && j.code===40000, j.message);
}

// ⑤ 超 10MB
{
  const big = Buffer.concat([png, Buffer.alloc(11*1024*1024)]);
  const fd = new FormData(); fd.append('files', new Blob([big],{type:'image/png'}), 'big.png');
  const r = await fetch(B+'/media/images',{method:'POST',headers:AUTH,body:fd});
  ok('⑤ 超10MB -> 400', r.status===400);
}

// ⑥ 越权与不存在
{
  const reg = await (await fetch(B+'/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:`other_${Date.now().toString(36).slice(-5)}`,password:'Str0ngPass!'})})).json();
  const AUTH2 = {Authorization:'Bearer '+reg.data.accessToken};
  let r = await fetch(B+'/media/'+globalThis.ids[0],{headers:AUTH2}); let j = await r.json();
  ok('⑥a 他人查询媒体 -> 40300', r.status===403 && j.code===40300);
  r = await fetch(B+'/media/999999999',{headers:AUTH2}); j = await r.json();
  ok('⑥b 不存在媒体 -> 40400', r.status===404 && j.code===40400);
}

// ⑦ 轮询形态与删除闭环
{
  let r = await fetch(B+'/media/'+globalThis.ids[1],{headers:AUTH});
  let j = await r.json();
  ok('⑦ owner 轮询 status=0 形态完整', j.code===0 && j.data.mediaId===globalThis.ids[1] && 'thumbUrl' in j.data);

  r = await fetch(B+'/media/'+globalThis.ids[1],{method:'DELETE',headers:AUTH});
  ok('⑦b owner 删除游离媒体 code=0', (await r.json()).code===0);
  r = await fetch(B+'/media/'+globalThis.ids[1],{headers:AUTH});
  ok('⑦c 删除后再查 -> 40400', (await r.json()).code===40400);
}

console.log(`\n==== PASS=${pass} FAIL=${fail} ====`);
process.exit(fail?1:0);
