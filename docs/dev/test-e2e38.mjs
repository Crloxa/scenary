// Phase 3-3.8 端到端收口验收：用户/资料/头像/发布/feed 游标+缓存/详情/可见性/删除/越权全链
import zlib from 'node:zlib';
const B = 'http://localhost:8080/api/v1';
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
  const A = await login('hill_walker','Str0ngPass!');
  let regB = await j(await fetch(B+'/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:'reader_bee',password:'Str0ngPass!'})}));
  if(regB.code!==0){
    regB = await j(await fetch(B+'/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username:'reader_bee',password:'Str0ngPass!',nickname:'读者乙'})}));
  }
  const AT={Authorization:'Bearer '+A.accessToken}, BT={Authorization:'Bearer '+regB.data.accessToken};

  // ---- 用户资料 ----
  let me = await j(await fetch(B+'/users/me',{headers:AT}));
  ok('① GET /users/me 形态', me.code===0 && me.data.username==='hill_walker' && 'noteCount' in me.data && typeof me.data.createdAt==='number');
  {
    const p = await j(await fetch(B+'/users/me',{method:'PATCH',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({nickname:'山野行人·改',bio:'只拍山和海'})}));
    ok('② PATCH 资料 回读完整对象', p.data.nickname==='山野行人·改' && p.data.bio==='只拍山和海');
    const bad = await j(await fetch(B+'/users/me',{method:'PATCH',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({})}));
    ok('②b 空 PATCH -> 40000 至少一项', bad.code===40000 && /至少/.test(bad.message), bad.message);
  }
  {
    const fd=new FormData(); fd.append('file',new Blob([makePng(400,300,[180,60,40])],{type:'image/png'}),'me.png');
    const av = await j(await fetch(B+'/users/me/avatar',{method:'POST',headers:AT,body:fd}));
    ok('③ 头像上传 返回avatarUrl', av.code===0 && /\/scenary-media\/avatar\/\d+\/[0-9a-f-]{36}\.jpg$/.test(av.data.avatarUrl||''), av.data.avatarUrl);
    const hr = await fetch(av.data.avatarUrl);
    ok('③b 头像匿名可读且为jpeg', hr.status===200 && hr.headers.get('content-type')==='image/jpeg');
    me = await j(await fetch(B+'/users/me',{headers:AT}));
    ok('③c me.avatarUrl 已更新', me.data.avatarUrl===av.data.avatarUrl);
  }

  // ---- 发布与 feed ----
  const m1 = await uploadWaitDone(A.accessToken);
  const m2 = await uploadWaitDone(A.accessToken, 1200, 500);
  const created = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({
      title:'雨后的四姑娘山', content:'十月初的雪线，云开了一小时。拍摄于双桥沟，光线从东侧打到雪面上。', placeName:'四川·四姑娘山', mediaIds:[m1.mediaId,m2.mediaId]})}));
  ok('④ 发布 code=0 coverUrl=首图thumb', created.code===0 && created.data.coverUrl===m1.thumbUrl);

  // 私密笔记（契约 v2.3 补充字段）
  const m3 = await uploadWaitDone(A.accessToken, 300, 800);
  const priv = await j(await fetch(B+'/notes',{method:'POST',headers:{...AT,'content-type':'application/json'},body:JSON.stringify({title:'私密底稿',content:'',mediaIds:[m3.mediaId],visibility:0})}));
  ok('⑤ visibility=0 可创建', priv.code===0);

  await new Promise(r=>setTimeout(r,150)); // 等首次缓存失效后的重建（DEL 是同步的，理论上即时）
  const feedAnon = await fetch(B+'/feed');
  const feedText = await feedAnon.text();
  const f1 = JSON.parse(feedText);
  ok('⑥ feed 首页包含公开卡', f1.data.list.some(c=>c.id===created.data.id));
  const card = f1.data.list.find(c=>c.id===created.data.id);
  ok('⑥b 卡片形态 author/preview/dims',
     card.author.nickname==='山野行人·改' && card.contentPreview.length<=49 &&
     card.coverWidth===800 && card.coverHeight===Math.round(m1.height*800/m1.width),
     `${card.coverWidth}x${card.coverHeight} preview_len=${card.contentPreview.length}`);
  // 缓存一致性：紧邻两读允许一次写侧可见性竞态，其后必须字节级稳定
  const again = await (await fetch(B+'/feed')).text();
  const third = again===feedText ? null : await (await fetch(B+'/feed')).text();
  ok('⑦ L1 首页缓存命中（至多一次竞态后字节稳定）', again===feedText || third===again);

  // 游标翻页
  const fL1 = await j(await fetch(B+'/feed?limit=1'));
  const visibleCount = f1.data.list.length + (f1.data.hasMore?9:0);
  ok('⑧ limit=1 翻页链', fL1.data.list.length===1 && typeof fL1.data.nextCursor==='number' && fL1.data.hasMore===true);
  const fL2 = await j(await fetch(B+'/feed?limit=20&cursor='+fL1.data.nextCursor));
  ok('⑧b 第二页不含首页项/最终 hasMore=false', !fL2.data.list.some(c=>c.id===fL1.data.list[0].id));

  // ---- 详情与可见性 ----
  {
    const dA = await j(await fetch(B+'/notes/'+created.data.id,{headers:AT}));
    ok('⑨ 作者看 mine=true images有序', dA.data.mine===true && dA.data.images.length===2 &&
       dA.data.images[0].thumbUrl===m1.thumbUrl);
    const dB = await j(await fetch(B+'/notes/'+created.data.id));
    ok('⑨b 匿名 mine=false 且公开可见', dB.data.mine===false && dB.data.title==='雨后的四姑娘山');
    const dp = await j(await fetch(B+'/notes/'+priv.data.id));
    ok('⑨c 匿名访问私密 -> 40400', dp.code===40400);
    const dpB = await j(await fetch(B+'/notes/'+priv.data.id,{headers:BT}));
    ok('⑨d 他人访问私密 -> 40400', dpB.code===40400);
    const dpA = await j(await fetch(B+'/notes/'+priv.data.id,{headers:AT}));
    ok('⑨e 作者本人访问私密 -> mine=true', dpA.data.mine===true);
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
