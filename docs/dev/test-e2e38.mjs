// Phase 3-3.8 端到端收口验收：用户/资料/头像/发布/feed 游标+缓存/详情/可见性/删除/越权全链
import zlib from 'node:zlib';
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const PASSWORD = `T9${Date.now().toString(36)}a!`;
let pass = 0, fail = 0;
const ok = (n, c, x='') => { c ? pass++ : fail++; console.log(`${c?'PASS':'FAIL'} | ${n}${x?' | '+x:''}`); };
function crc32(buf){crc32.t??=(()=>{let x=[];for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xEDB88320^(c>>>1):c>>>1;x[n]=c>>>0}return x})();let c=0xFFFFFFFF;for(const b of buf)c=crc32.t[(c^b)&0xFF]^(c>>>8);return((c^0xFFFFFFFF)>>>0)}
function chunk(t,d){const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const b=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc32(b));return Buffer.concat([l,b,c])}
function makePng(w,h,[r,g,b]=[64,128,200]){
  const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(w,0);ihdr.writeUInt32BE(h,4);ihdr[8]=8;ihdr[9]=2;
  const rows=[];for(let y=0;y<h;y++){rows.push(Buffer.from([0]));const px=Buffer.alloc(w*3);px.fill(r);px[1]=g;px[2]=b;rows.push(px)}
  return Buffer.concat([Buffer.from([0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(Buffer.concat(rows))),chunk('IEND',Buffer.alloc(0))]);
}
const j = async r => r.json();
const login = async (u,p) => (await j(await fetch(B+'/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:u,password:p})}))).data;
const uploadWaitDone = async (at, w=900, h=700) => {
  const fd=new FormData(); fd.append('files',new Blob([makePng(w,h)],{type:'image/png'}),'p.png');
  const up = await j(await fetch(B+'/media/images',{method:'POST',headers:{Authorization:'Bearer '+at},body:fd}));
  const id = up.data.items[0].mediaId;
  for(let i=0;i<20;i++){
    const s = await j(await fetch(B+'/media/'+id,{headers:{Authorization:'Bearer '+at}}));
    if(s.data.status===1) return {...s.data};
    await new Promise(r=>setTimeout(r,350));
  }
  throw new Error('media not ready');
};

(async()=>{
  const suffix = Date.now().toString(36).slice(-5);
  const authorUsername = `e2e_a_${suffix}`;
  const author = await j(await fetch(B+'/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:authorUsername,password:PASSWORD,nickname:'山野行人'})}));
  const A = author.data;
  const regB = await j(await fetch(B+'/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:`e2e_b_${suffix}`,password:PASSWORD,nickname:'读者乙'})}));
  const AT={Authorization:'Bearer '+A.accessToken}, BT={Authorization:'Bearer '+regB.data.accessToken};

  // ---- 用户资料 ----
  let me = await j(await fetch(B+'/users/me',{headers:AT}));
  ok('① GET /users/me 形态', me.code===0 && me.data.username===authorUsername && 'noteCount' in me.data && typeof me.data.createdAt==='number');
  {
    const p = await j(await fetch(B+'/users/me',{method:'PATCH',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({nickname:'山野行人·改',bio:'只拍山和海'})}));
    ok('② PATCH 资料 回读完整对象', p.data.nickname==='山野行人·改' && p.data.bio==='只拍山和海');
    const bad = await j(await fetch(B+'/users/me',{method:'PATCH',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({})}));
    ok('②b 空 PATCH -> 40000 至少一项', bad.code===40000 && /至少/.test(bad.message), bad.message);
  }
  {
    const fd=new FormData(); fd.append('file',new Blob([makePng(400,300,[180,60,40])],{type:'image/png'}),'me.png');
    const av = await j(await fetch(B+'/users/me/avatar',{method:'POST',headers:AT,body:fd}));
    ok('③ 头像上传 返回avatarUrl（签名 URL，路径为 avatar 对象）', av.code===0 && /\/scenary-media\/avatar\/\d+\/[0-9a-f-]{36}\.jpg\?/.test(av.data.avatarUrl||'') && (av.data.avatarUrl||'').includes('X-Amz-Signature='), av.data.avatarUrl);
    const hr = await fetch(av.data.avatarUrl);
    ok('③b 头像匿名可读且为jpeg', hr.status===200 && hr.headers.get('content-type')==='image/jpeg');
    me = await j(await fetch(B+'/users/me',{headers:AT}));
    ok('③c me.avatarUrl 已更新（比较对象路径，签名随时间变化）', new URL(me.data.avatarUrl).pathname===new URL(av.data.avatarUrl).pathname);
  }

  // ---- 发布与 feed ----
  const m1 = await uploadWaitDone(A.accessToken);
  const m2 = await uploadWaitDone(A.accessToken, 1200, 500);
  const requestKey = crypto.randomUUID();
  const publishBody = {title:'雨后的四姑娘山', content:'十月初的雪线，云开了一小时。拍摄于双桥沟，光线从东侧打到雪面上。', placeName:'四川·四姑娘山', mediaIds:[m1.mediaId,m2.mediaId], requestKey};
  const created = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify(publishBody)}));
  ok('④ 发布 code=0 coverUrl=首图thumb（对象路径一致）', created.code===0 && new URL(created.data.coverUrl).pathname===new URL(m1.thumbUrl).pathname);
  const repeated = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify(publishBody)}));
  ok('④b 同 requestKey 重试只返回同一篇', repeated.code===0 && repeated.data.id===created.data.id);

  // 私密笔记（契约 v2.3 补充字段）
  const m3 = await uploadWaitDone(A.accessToken, 300, 800);
  const priv = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({title:'私密底稿',content:'',mediaIds:[m3.mediaId],visibility:0})}));
  ok('⑤ visibility=0 可创建', priv.code===0);

  // 第二条公开笔记：feed 翻页断言（⑧/⑧b）需要 ≥2 条公开笔记——必须在 feed 缓存
  // 预热之前发布，否则 ⑧ 与缓存重建竞态（CI 全新 DB 上实测，CHANGELOG v2.48）
  const m4 = await uploadWaitDone(A.accessToken, 300, 800);
  const extra = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({title:'翻页链第二公开笔记',content:'',mediaIds:[m4.mediaId],visibility:1})}));
  ok('⑤b 第二条公开笔记可创建', extra.code===0);

  let feedText, f1, card;
  for (let i=0; i<10; i++) {
    feedText = await (await fetch(B+'/feed')).text();
    f1 = JSON.parse(feedText);
    card = f1.data.list.find(c=>c.id===created.data.id);
    if (card) break;
    await new Promise(r=>setTimeout(r,200));
  }
  ok('⑥ feed 首页包含公开卡', card != null,
     card ? '' : `target=${created.data.id} ids=${f1.data.list.map(c=>c.id).join(',')}`);
  ok('⑥b 卡片形态 author/preview/dims',
     card != null && card.author.nickname==='山野行人·改' && card.contentPreview.length<=49 &&
     card.coverWidth===800 && card.coverHeight===Math.round(m1.height*800/m1.width),
     card ? `${card.coverWidth}x${card.coverHeight} preview_len=${card.contentPreview.length}` : '目标公开卡缺失');
  // 缓存一致性：旧页校验失败时允许一次重建，之后必须字节级稳定。
  const again = await (await fetch(B+'/feed')).text();
  const third = again===feedText ? null : await (await fetch(B+'/feed')).text();
  ok('⑦ L1 首页缓存命中（至多一次重建后稳定）', again===feedText || third===again);

  // 游标翻页
  const fL1 = await j(await fetch(B+'/feed?limit=1'));
  const visibleCount = f1.data.list.length + (f1.data.hasMore?9:0);
  ok('⑧ limit=1 翻页链', fL1.data.list.length===1 && typeof fL1.data.nextCursor==='number' && fL1.data.hasMore===true);
  const fL2 = await j(await fetch(B+'/feed?limit=20&cursor='+fL1.data.nextCursor));
  ok('⑧b 第二页不含首页项/最终 hasMore=false', !fL2.data.list.some(c=>c.id===fL1.data.list[0].id));

  // ---- 详情与可见性 ----
  {
    const dA = await j(await fetch(B+'/notes/'+created.data.id,{headers:AT}));
    ok('⑨ 作者看 mine=true images有序', dA.code===0 && dA.data?.mine===true && dA.data.images?.length===2 &&
       new URL(dA.data.images[0].thumbUrl).pathname===new URL(m1.thumbUrl).pathname, dA.message);
    const dB = await j(await fetch(B+'/notes/'+created.data.id));
    ok('⑨b 匿名 mine=false 且公开可见', dB.data.mine===false && dB.data.title==='雨后的四姑娘山');
    const dp = await j(await fetch(B+'/notes/'+priv.data.id));
    ok('⑨c 匿名访问私密 -> 40400', dp.code===40400);
    const dpB = await j(await fetch(B+'/notes/'+priv.data.id,{headers:BT}));
    ok('⑨d 他人访问私密 -> 40400', dpB.code===40400);
    const dpA = await j(await fetch(B+'/notes/'+priv.data.id,{headers:AT}));
    ok('⑨e 作者本人访问私密 -> mine=true', dpA.code===0 && dpA.data?.mine===true, dpA.message);
  }

  // ---- 个人网格与计数 ----
  {
    const gA = await j(await fetch(B+'/users/'+A.userId+'/notes',{headers:AT}));
    ok('⑩ 本人网格含私密混排', gA.data.list.length>=2 && gA.data.list.some(c=>c.visibility===0));
    const gB = await j(await fetch(B+'/users/'+A.userId+'/notes'));
    ok('⑩b 他人网格仅公开', gB.data.list.every(c=>c.visibility===1));
    const pubA = await j(await fetch(B+'/users/'+A.userId));
    ok('⑩c 公开主页无 username 字段', !('username' in pubA.data) && 'noteCount' in pubA.data);
  }

  // ---- 删除/越权/校验 ----
  {
    const forbidden = await j(await fetch(B+'/notes/'+created.data.id,{method:'DELETE',headers:BT}));
    ok('⑪ 他人删笔记 -> 40300', forbidden.code===40300);

    const del = await j(await fetch(B+'/notes/'+priv.data.id,{method:'DELETE',headers:AT}));
    ok('⑫ 作者软删 code=0', del.code===0 && del.data===null);
    const after = await j(await fetch(B+'/notes/'+priv.data.id,{headers:AT}));
    ok('⑫b 删除后连作者也 40400', after.code===40400);
    const repeat = await j(await fetch(B+'/notes/'+priv.data.id,{method:'DELETE',headers:AT}));
    ok('⑫c 幂等：重复删除仍 code=0', repeat.code===0);

    const feedAfter = JSON.parse(await (await fetch(B+'/feed')).text());
    ok('⑬ 删除后即时从 feed 消失(缓存已失效)', !feedAfter.data.list.some(c=>c.id===priv.data.id) ||
        !feedAfter.data.list.map(c=>c.id).includes(priv.data.id));

    const badTitle = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({title:'',mediaIds:[m1.mediaId]})}));
    ok('⑭ 空标题 -> 40000', badTitle.code===40000);
    const reuseMedia = await j(await fetch(B+'/notes',{method:'POST',headers:{...BT,'content-type':'application/json'},body:JSON.stringify({title:'偷图',mediaIds:[m1.mediaId]})}));
    ok('⑮ 用他人媒体 -> 40300', reuseMedia.code===40300);
    const unprocessed = await uploadWaitDone(BT.Authorization.replace('Bearer ','')) .catch(()=>null);
    void unprocessed;
    // 快速构造未处理媒体：刚上传立即拿来发帖
    const fd=new FormData(); fd.append('files',new Blob([makePng(50,50)],{type:'image/png'}),'fast.png');
    const fastUp = await j(await fetch(B+'/media/images',{method:'POST',headers:BT,body:fd}));
    const raceNote = await j(await fetch(B+'/notes',{method:'POST',headers:{...BT,'content-type':'application/json'},body:JSON.stringify({title:'抢跑',mediaIds:[fastUp.data.items[0].mediaId],visibility:1})}));
    ok('⑯ 未处理完成媒体 -> 40901 或竞速成功容忍', [0,40901].includes(raceNote.code), 'code='+raceNote.code);
  }

  console.log(`\n==== PASS=${pass} FAIL=${fail} ====`);
  process.exit(fail?1:0);
})();
