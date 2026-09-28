# 25 · WebKit 路由拦截与 E2E 时序：多路由 LIFO 链 + expect 轮询的偶发 XHR 无响应

- **日期**：2026-09-19 · 所属 P12-E3
- **现象**：`p12-e3-place.spec.js` 在 Chromium/Firefox 12/12 稳定，WebKit 上却出现两类伪影：① 断言超时但页面快照显示元素其实已渲染（部分用例）；② 失败 trace 里 `**/api/v1/places/reverse-geocode` 的 XHR `status: -1`（请求发出、永无响应），同页 `/notifications` 却正常被 mock 应答。单跑稳定、多 spec 并行高负载下偶发率约 40%。
- **原因**：三个因素叠加，全部是测试侧而非应用侧——
  1. **多路由 LIFO 链**：beforeEach 注册了一个默认 geocode 路由、用例内又注册第二个"覆盖"路由。Playwright 后注册者先执行，WebKit 下这条"两匹配一 fulfill"的链比单路由更容易把请求挂起（trace 证据：`setNetworkInterceptionPatterns` 注册成功、请求被捕获、但无 `fulfill` 动作、`response.status=-1`）。
  2. **expect 轮询 vs 平铺等待**：`toBeVisible({timeout})` 的注入脚本轮询与挂起的拦截请求组合时，WebKit 表现出比"平铺 waitForTimeout 后一次性断言"更高的失败率。
  3. **无头 WebKit 的定时器节流**：应用侧 600ms 防抖 setTimeout 在无头页/资源争用下可能推迟到秒级，3000ms 断言窗口在高负载下不够。
  另外还踩了两个非 WebKit 的坑，一并记：**Java double 序列化**（backend 转发 `5` 变 `lat=5.0`，mock 用字符串 `=== '5'` 匹配不中，需数值比较）；**缓存反噬**（第一版 mock 秒回"成功地名"被正确缓存 30 天，第二次跑超时用例变成缓存秒回——验收脚本必须理解自己依赖的缓存语义）。
- **原理**：Playwright 路由是"后注册先执行"的处理链，链上任何一环既不 fulfill 也不 continue 时请求永久挂起；WebKit 的网络拦截实现与 Chromium 路径不同，对链式匹配与注入轮询的竞态更敏感。无头浏览器页面对 `setTimeout` 有最小间隔节流（页面不可见时链式定时器 ≥1s）。
- **我怎么验证的**：
  1. 失败 trace 解包看 `0-trace.network`：geocode 请求 `-1`、notifications `200`、WS `101`——锁定"仅这条路由被挂起"而非全局网络问题；
  2. `0-trace.trace` 动作流：`setNetworkInterceptionPatterns` ×2 → goto → 只有一次 fulfill（notifications）→ fills → expect 超时，确认路由处理器从未执行；
  3. 对照实验：把 spec 改成"每页只注册一个 geocode 路由（应答内容经变量注入）+ 平铺 `waitForTimeout(2000)` + 事后一次性断言"，WebKit 连跑 5×12/12 全绿；单独复跑原形态 ① 用例 3/3 过（并行负载是放大器，不是根因）；
  4. 反向验证：串行模式（worker 内页面复用导致路由注册累积）反而更糟——排除了"路由越多越稳"的错误直觉。
- **可复用结论**：
  1. **E2E 路由 mock 纪律：一个 URL 模式全页只注册一次处理器**，差异化应答用外部变量注入（`geocodeReply`），不要用"beforeEach 注册默认 + 用例内覆盖"的 LIFO 链；
  2. **防抖类交互的断言用"平铺等待 + 事后断言"**，等待时长 = 防抖 + 网络往返 + 节流余量（本仓库取 2000ms），不要依赖 expect 轮询穿透定时器节流；
  3. **新增前端 API 调用必须同步给所有会走到该页面的既有 e2e spec 补 mock**（本次 p12-video.spec 漏补导致假令牌 401 → 全局刷新流程卡死发布页——401 拦截器是全局副作用，测试假令牌不是"无害令牌"）；
  4. **mock provider 的行为要按数值语义写**（跨语言序列化差异），且验收前清空相关缓存键（`redis-cli --scan --pattern 'place:rg:*'`），避免上一轮运行的结果污染本轮断言；
  5. trace 的 `0-trace.network`/`0-trace.trace` 是判定"应用 bug 还是测试伪影"的最短路径：`status:-1` + 无 fulfill 动作 = 拦截层挂起，不是后端问题。
  6. **（E4 期补强）"平铺等待 + 同步断言"仍是漏的**：同步断言（如路由计数 ≥1）在节流下随时可能踩空，凡断言"某异步事实已发生"一律用 `expect.poll`/自带轮询的 web 断言；平铺等待只作前置余量不作判定依据。E3-③ 用 expect.poll 修复后组合矩阵 5×36/36 连绿。
