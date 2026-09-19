# 21 · P15 限流归因与账号注销的 bean 环

> 关联 Phase：P15 账号与安全基线（2026-09-12 完成）。对应改动：`common/RateLimitService`、`AuthController.clientIp`、`AccountDeactivationService`。

## 现象

两个坑先后出现：

1. 审计探针连发 6 次注册全部成功，据此断言"注册无限流"；但 P15 施工时读码发现 `AuthService.enforceRegisterRateLimit` 一直存在（20 次/IP/小时）。结论与代码事实冲突。
2. 给 `UserService` 注入 `CommentService` 实现注销软删后，Compose 重建的 backend 进入重启循环，启动日志报 `Requested bean is currently in creation`：`commentService ↔ userService` 成环。

## 原因

1. **限流键的 IP 归因错了**：nginx 的 `/api/` location 只传了 `X-Real-IP`，没传 `X-Forwarded-For`；后端 `clientIp()` 无 XFF 时取 `request.getRemoteAddr()`——在 Compose 里这是 nginx 容器的 IP。于是"20 次/IP/小时"实际是"全站共享 20 次/小时"：探针的 6 次落在共享桶里远没到阈值，看起来像"没有限流"。审计探针只验证了"触发没有"，没验证"计数键落在谁头上"。
2. **跨模块门面画了个圈**：注销需要"用户匿名化 + 笔记软删 + 评论软删"。笔记方向本来就有 `UserService → NoteService`；我顺手把评论方向也接成 `UserService → CommentService`，而 P10 起评论模块早已依赖 `CommentService → UserService`（ensureActive/author）。两个方向的依赖一拼，Spring Boot 2.6+ 默认禁止循环引用，直接拒绝启动。

## 原理

1. **真实客户端 IP 是"信任链末端的段"**：`$proxy_add_x_forwarded_for` 把 nginx 看到的远端地址追加到客户端可能伪造的 XFF 之后，所以后端要取**最后一段**（本方反代追加的那段），取首段等于信任攻击者自报的地址。回环/网关地址（127.0.0.1、172.19.0.1）不是真实用户，需要豁免或放宽容忍，否则开发与测试矩阵会自己把自己锁死。
2. **环的解法不是 allow-circular-references，而是拆职责**：注销是"编排"而非"用户资料域"的职责。把密码确认、匿名化、软删、头像对象删除收进独立的 `AccountDeactivationService`（user 包），它单向依赖 note/comment 门面；令牌吊销是 Redis 操作，由 Controller 在数据库事务提交后调用 auth 门面，不进事务。环就断了，事务边界也更干净。

## 我怎么验证的

1. `docker compose exec redis redis-cli get rl:register:172.19.0.1`：修前看到全站共享键；修后黑盒 `test-p15-security.mjs` ⑤ 段注册 201 次触发 429、清理计数键后恢复。
2. 130 连发点赞：`{"404":120,"429":5}`，第 121 次起 429，窗口/阈值与 02 §1.4 契约一致。
3. 循环依赖：修复前 backend 容器 restart loop，日志含 bean 环图；修复后 `mvn test` 68/68、Compose healthy、12 个黑盒套件 147/147 全绿。
4. 密码重置脚本端到端：注册 → `ops/reset-user-password.ps1` 重置 → 旧密码 40000、新密码 code=0。

## 可复用结论

- 判断"某防护缺失"前，先看计数键落在哪个维度（`redis-cli keys rl:*`），再下结论；探针要覆盖到阈值才有证明力。
- 反向代理必须传 `X-Forwarded-For`，后端取末段；凡是按 IP 限流/审计的功能，部署形态（有无反代、几层反代）是前置设计输入。
- 给既有模块加"反向依赖"前先画依赖图：A→B 与 B→A 共存即环。拆出一个单向的编排服务，比打开 `allow-circular-references` 或加 `@Lazy` 干净得多。
- Redis 操作不进数据库事务：吊销/失效类操作放在事务提交之后，失败语义才说得清。
