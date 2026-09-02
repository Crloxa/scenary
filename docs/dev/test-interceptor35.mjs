// Phase 3-3.5 拦截链验收
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const PASSWORD = `T9${Date.now().toString(36)}a!`;
let pass=0, fail=0;
const ok=(n,c,x='')=>{c?pass++:fail++;console.log(`${c?'PASS':'FAIL'} | ${n}${x?' | '+x:''}`)};
const post=(p,b,h={})=>fetch(B+p,{method:'POST',headers:{'content-type':'application/json',...h},body:typeof b==='string'?b:JSON.stringify(b)}).then(r=>r.json());
const send=(m,p,h={})=>fetch(B+p,{method:m,headers:h}).then(async r=>[r.status, await r.json()]);

(async()=>{
  let [s,r] = await send('GET','/users/me');
  ok('① 无token -> 401/40100', s===401 && r.code===40100, `http=${s} code=${r.code}`);

  [s,r] = await send('GET','/users/me',{Authorization:'Basic dXNlcjpwd2Q='});
  ok('② 非Bearer方案 -> 40100', s===401 && r.code===40100);

  [s,r] = await send('GET','/users/me',{Authorization:'Bearer not.a.real.jwt'});
  ok('③ 垃圾JWT -> 40101', s===401 && r.code===40101);

  const reg = await post('/auth/register',{username:`itc_${Date.now().toString(36).slice(-5)}`,password:PASSWORD});
  ok('④ 注册准备用户', reg.code===0);
  const at1=reg.data.accessToken;

  [s,r] = await send('GET','/users/me',{Authorization:'Bearer '+at1});
  ok('⑤ 有效access -> 200 回显正确身份', s===200 && r.data.id===reg.data.userId && String(r.data.username).startsWith('itc_'), JSON.stringify(r.data));

  // refresh 当 access 用 -> 40101（类型错误）
  const rt1=reg.data.refreshToken;
  [s,r] = await send('GET','/users/me',{Authorization:'Bearer '+rt1});
  ok('⑥ refresh当access -> 40101', s===401 && r.code===40101);

  // 黑名单：登出后同一 access 立即失效
  await post('/auth/logout', {}, {Authorization:'Bearer '+at1});
  [s,r] = await send('GET','/users/me',{Authorization:'Bearer '+at1});
  ok('⑦ 已登出access(黑名单) -> 40101', s===401 && r.code===40101, r.message);

  // 连发两次确认 ThreadLocal 无残留影响（第二个请求独立解析）
  const second = await post('/auth/register',{username:`itc2_${Date.now().toString(36).slice(-5)}`,password:PASSWORD});
  [s,r] = await send('GET','/users/me',{Authorization:'Bearer '+second.data.accessToken});
  const [s2,r2] = await send('GET','/users/me',{Authorization:'Bearer '+second.data.accessToken});
  ok('⑧ 连续请求无串号(ThreadLocal清理生效)', s===200&&r.data.id===second.data.userId&&s2===200&&r2.data.id===second.data.userId);

  console.log(`\n==== PASS=${pass} FAIL=${fail} ====`);
  process.exit(fail?1:0);
})();
