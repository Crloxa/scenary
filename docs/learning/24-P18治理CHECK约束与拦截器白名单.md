# 24 · P18 治理闭环：CHECK 约束与拦截器白名单的两个隐形坑

> 2026-09-19 · P18 举报与屏蔽施工中踩坑，亲测可复现。

## 现象

两个互不相关的 50000/40100，都在"给既有系统加新状态/新端点"时出现：
1. 举报达阈值执行 `UPDATE notes SET visibility=3` → `Check constraint 'chk_notes_visibility' is violated`（50000）；
2. `POST /api/v1/reports` 一律 40100，但同批的 `/users/*/block` 正常。

## 原因

1. V1 建表时的 `chk_notes_visibility CHECK (visibility IN (0,1,2))` 是当时状态机的固化；P18 新增状态 3 时，改代码不改库约束，UPDATE 在约束上炸。且 V10 已经被 Flyway 应用，**不能改历史迁移文件**（checksum 保护），只能追加 V11 做 `DROP CHECK` + `ADD CONSTRAINT ... IN (0,1,2,3)`。
2. 本项目 AuthInterceptor 是**路径白名单**（addPathPatterns 枚举受保护路径），不在名单里 = 匿名可达。`/users/*/block` 我记得加了，`/reports` 忘了——UserContext.require() 拿不到登录态抛 40100。

## 原理

- 状态机扩展的成本不在枚举值本身，而在**所有固化了状态集合的地方**：DB CHECK、Java switch/else-if、前端字典、缓存键语义。任何一处漏改都是运行时炸点。
- 白名单式鉴权的安全性来自"默认拒绝"，代价是**每加一个需登录端点都要同步登记**；忘登记不会报错，只会安静地把新端点暴露成匿名接口（本项目因 require() 二次兜底才变成 40100，属侥幸）。

## 我怎么验证的

1. 坑 1：`docker compose logs backend` 拿到 `Check constraint 'chk_notes_visibility' is violated`；`SHOW CREATE TABLE notes` 确认约束定义；新增 V11 后 `docker compose up -d --build backend`，黑盒第 5 个举报触发隐藏返回 `{"created":true,"hidden":true}`；
2. 坑 2：对比 `/users/*/block`（已登记，PUT 返回 0）与 `/reports`（未登记，POST 返回 40100）的行为差；在 `WebConfig` 补 `"/api/v1/reports"` 后复跑黑盒 19/19；
3. 顺带验证了 `UserContext.require()` 作为白名单遗漏的最后防线会抛 40100 而不是 NPE。

## 可复用结论

- 新增枚举/状态值时，用 `grep -rn "visibility" --include="*.sql" --include="*.java" --include="*.xml"` 全量扫一遍固化点；DB CHECK 用 `SHOW CREATE TABLE` 核对。
- 新增写端点的自检三问：拦截器名单加了吗？门面（Service）里 ensureActive 了吗？匿名访问的期望码是 401 吗？
- 迁移文件一旦被应用即冻结，演进一律追加新 Vn——checksum 保护不是负担，是防"历史重写"的护栏。
