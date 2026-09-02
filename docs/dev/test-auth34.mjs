// Phase 3-3.4 认证验收脚本：register/dup/validation/login/rotate-replay/logout-revoke/lock
const B = process.env.SCENARY_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const PASSWORD = `T9${Date.now().toString(36)}a!`;
let pass = 0, fail = 0;
const ok = (name, cond, extra='') => { cond ? pass++ : fail++; console.log(`${cond?'PASS':'FAIL'} | ${name}${extra?' | '+extra:''}`); };
const post = async (path, body, headers={}) => {
  const r = await fetch(B+path, {method:'POST', headers:{'content-type':'application/json',...headers}, body: typeof body==='string'?body:JSON.stringify(body)});
  return r.json();
};

(async () => {
  const SUF = Date.now().toString(36).slice(-5);   // 每次运行独立用户名，保证可重复执行
  // ① 注册（含中文昵称，验证服务端 UTF-8 处理）
  let r = await post('/auth/register', {username:`h2_${SUF}`, password:PASSWORD, nickname:'山野行人'});
  ok('注册 code=0', r.code===0, r.code===0 ? `userId=${r.data.userId}` : r.message);
  ok('昵称中文回显', r.data?.nickname==='山野行人');
  ok('accessExpiresIn=7200', r.data?.accessExpiresIn===7200);
  ok('refreshExpiresIn=2592000', r.data?.refreshExpiresIn===2592000);
  const at1=r.data.accessToken, rt1=r.data.refreshToken;

  // ② 重名
  r = await post('/auth/register', {username:`h2_${SUF}`, password:PASSWORD});
  ok('重名注册 code=41001', r.code===41001);

  // ③ 非法用户名
  r = await post('/auth/register', {username:'ab', password:PASSWORD});
  ok('非法用户名 code=40000', r.code===40000);

  // ④ 登录错密 → 统一文案
  r = await post('/auth/login', {username:`h2_${SUF}`, password:`${PASSWORD}x`});
  ok('错误密码 code=40000 文案统一', r.code===40000 && r.message==='用户名或密码错误', r.message);

  // ⑤ 正确登录
  r = await post('/auth/login', {username:`h2_${SUF}`, password:PASSWORD});
  ok('正确登录 code=0', r.code===0);
  const rtLogin = r.data.refreshToken;

  // ⑥ 刷新旋转：旧 RT 用一次成功
  r = await post('/auth/refresh', {refreshToken: rt1});
  ok('首次刷新 code=0 且新令牌不同', r.code===0 && r.data.refreshToken!==rt1);
  const rtRotated = r.data.refreshToken;

  // ⑦ 重放旧 RT → 40101
  r = await post('/auth/refresh', {refreshToken: rt1});
  ok('旧 refreshToken 重放 code=40101', r.code===40101);

  // ⑧ 登出（双保险），随后未使用的白名单 RT 必须失效
  r = await post('/auth/login', {username:`h2_${SUF}`, password:PASSWORD});
  const at3=r.data.accessToken, rt3=r.data.refreshToken;
  r = await post('/auth/logout', {}, {Authorization:'Bearer '+at3});
  ok('登出 code=0', r.code===0 && r.data===null);
  r = await post('/auth/refresh', {refreshToken: rt3});
  ok('登出后刷新 code=40101(白名单已吊销)', r.code===40101);

  // ⑨ 错 5 次触发锁定 → 之后连正确密码也拒绝并给剩余秒数
  for (let i=0;i<5;i++) {
    r = await post('/auth/login', {username:`lock_${SUF}`, password:`${PASSWORD}x`});
    if (i<4) ok(`第${i+1}次错密=40000`, r.code===40000);
  }
  ok('第5次错密触发42001', r.code===42001, r.message);
  r = await post('/auth/login', {username:`lock_${SUF}`, password:PASSWORD});
  ok('锁定期内正确密码也42001', r.code===42001, r.message);

  console.log(`\n==== PASS=${pass} FAIL=${fail} ====`);
  process.exit(fail?1:0);
})();
