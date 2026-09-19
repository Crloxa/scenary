# Scenary 全栈不足盘点审计 · 2026-09-11

> 定位：P12（含 E1）完成后的全项目缺陷盘点。目的有二：① 为"私有桶短时播放签名、逆地理编码、地图 UI"三项既定增强补实测依据；② brainstorm 并实测验证其他潜在不足，回答"是否需要新的开发计划"（结论：需要，立项 P15）。
> 证据纪律：本文按 [evidence/README.md](../evidence/README.md) 规范记录环境、命令、结果与边界；探针脚本为临时脚本，P15-06 落地时正规化为 `docs/dev/test-p15-security.mjs`。

## 1. 结论摘要

实测确认 **7 项不足**（其中 1 项隐私漏洞）+ 代码核查确认 **4 项结构性缺口**；已据此立项 4 个新阶段（[05 手册 v1.5](../05-后续开发路线图与实施手册.md)）：

| 新阶段 | 驱动发现 | 章节 |
|---|---|---|
| P12-E2 私有桶与短时签名 | 探针 3/4：私密笔记封面匿名可达、桶级匿名存在性探测 | 05 §6.4 |
| P12-E3 逆地理编码 | 探针 1c：`/places/reverse-geocode` 不存在（既定增强，补齐计划） | 05 §6.5 |
| P12-E4 地图 UI | 既定增强仅有边界描述，无任务拆解（本轮补齐计划） | 05 §6.6 |
| P15 账号与安全基线 | 探针 1a/1b/2/5/6 + 代码核查（找回/注销/IP 归因/写接口限流/安全头/通知清理） | 05 §7 |

## 2. 环境与工具

| 项 | 值 |
|---|---|
| 日期 | 2026-09-11 |
| 代码基线 | 仓库 `main` @ `a60bb2e`（feat(video): 完成P12-E1分片上传），工作区干净 |
| 部署形态 | `docker compose up -d --build`（:8081 唯一对外端口），六容器全部 Up/healthy |
| 测试工具 | Node v22.23.2 临时探针脚本（global fetch，无新增依赖）；代码核查用 grep/读源码 |
| 数据边界 | 开发栈持久卷（含 2026-09-04 验收数据）；探针随机用户名（`probe*` 前缀） |

## 3. 实测探针矩阵

探针脚本逻辑（等价命令描述，脚本本体为临时文件已清理）：

1. `POST /api/v1/auth/register` 注册随机用户并取 `accessToken`。
2. 依次请求缺失端点：`POST /auth/forgot-password`、`DELETE /users/me`、`GET /places/reverse-geocode`。
3. 连发 6 次不同用户名注册，统计 429 次数。
4. `POST /api/v1/media/images`（multipart 字段 `files`，1×1 PNG 夹具）→ 轮询 `GET /media/{id}` 直到 `thumbUrl` 出片 → `POST /notes`（`visibility: 0` 私密）→ 匿名 `GET` 该 `thumbUrl`。
5. 匿名 `GET /minio/scenary-media/orig/1970/<不存在对象>`，以 404/403 区分桶级匿名策略。
6. `GET /` 检查 5 项安全响应头。
7. 对私密笔记连发 30 次 `PUT/DELETE /notes/{id}/like`，统计 429 次数。

| # | 探针 | 期望（安全基线） | 实测结果 | 结论 |
|---|---|---|---|---|
| 0 | 注册登录 | 200 + token | 200 + token | 前置通过 |
| 1a | `POST /auth/forgot-password` | 存在找回链路 | **404** | 密码找回缺失（users 表无 email 字段，数据层即不可实现） |
| 1b | `DELETE /users/me` | 可注销 | **405** | 账号注销缺失 |
| 1c | `GET /places/reverse-geocode` | 存在 | **404** | 逆地理未实现（既定增强，本轮立项 E3） |
| 2 | 注册限流 | 有频率限制 | **6/6 全部成功，0 次 429** | 初判"注册无限流"**有误**（勘误见 §7）：`AuthService.enforceRegisterRateLimit` 一直存在（20 次/IP/小时），探针 6 次未达阈值。真实缺口是 **IP 归因缺陷**：nginx 未传 X-Forwarded-For，后端取到的是 nginx 容器 IP，Compose 下全站共享同一个 20 次/小时的注册配额，攻击者灌满即可对注册功能造成 DoS |
| 3 | 私密笔记封面匿名可达 | 403/404 | **200** | **隐私漏洞**：私密笔记缩略图为 public-read 桶直链，任何拿到 URL 者可读（noteId=188 复现） |
| 4 | 桶级匿名策略 | 匿名 403 | **404** | public-read 桶允许匿名探测任意对象存在性（404=可探测，403=私有） |
| 5 | 安全响应头 | 五项齐全 | **全部缺失**（HSTS/CSP/X-Frame-Options/X-Content-Type-Options/Referrer-Policy） | nginx 未配置任何安全头（HSTS 在 http 形态本就不适用，其余四项应补） |
| 6 | 社交写接口限流 | 有频率限制 | **30 次点赞切换 0 次 429** | 点赞/收藏/关注写接口无限流 |

## 4. 代码核查发现（静态确认，无需运行时）

| # | 发现 | 证据位置 | 影响 |
|---|---|---|---|
| C1 | 预签名能力只有 PUT（E1 分片上传用），无 GET 签名 | `backend/.../media/MinioService.java`（仅 `presignPut`） | E2 需新增 `presignGet`，但 `presignClient`/`addPathPrefix` 同源反代机制可直接复用 |
| C2 | URL 被持久化进 DB 与缓存 | `media.thumb_url/url`、`notes.cover_url`、`users.avatar_url`；feed 两级缓存持有 `NoteCardVO.coverUrl` | E2 的核心迁移点：改"存 key、读时签名"，否则签名 URL 过期即死链 |
| C3 | 限流仅覆盖登录与评论两处 | `AuthService`、`CommentService.enforceRateLimit`（Redis INCR + `ErrorCode.TOO_MANY_REQUESTS`） | 模式已验证可直接推广为 `common.RateLimitService`（P15-01） |
| C4 | 通知表无任何清理/保留策略 | `notification` 包内无 delete 逻辑 | 无限增长；P15-05 加定时清理 |
| C5 | 根目录 `test-results/` 未入 `.gitignore`（frontend/test-results 已忽略，根目录 Playwright 输出未忽略） | `.gitignore` | 已在本轮顺手修复（v2.39） |
| C6 | `index.html` 存在 2 处内联 `<script>` | `frontend/index.html`（FOUC 预判脚本） | CSP 不能直接 `default-src 'self'` 一禁了之，P15-02 先 Report-Only |

## 5. 明确不算缺陷的项（核对过边界）

- 推荐算法、私信、管理后台、地图 API、标签/话题：MVP 范围明确排除或长期 TODO（README §MVP 范围、05 §长期 TODO）。
- 头像上传：`POST /users/me/avatar` 已存在（`UserController`）。
- CI：`.github/workflows/ci.yml` 已覆盖后端单测、前端单测/构建/浏览器、compose config、密钥扫描；黑盒全栈矩阵依赖 Compose 环境，仍以本地验收为准（与既有证据链一致）。
- 图片上传无直传/分片：单图 10MB 上限下后端代理可接受，E1 刻意收敛在视频（05 §6.1）。
- 搜索受控 LIKE 无中文分词：P11 已评估并记录理由（05 §5.1），待真实查询样本再议 ES。

## 6. 立项决策与残留

- **是否需要新的开发计划：需要。** 三项既定增强（E2/E3/E4）补齐了任务级计划（03 附 7~9）；实测新发现的账号生命周期与滥用面缺口合并立项为 **P15 账号与安全基线**（03 附 10、05 §7）。P13/P14 维持长期 TODO 不变。
- **探针残留**：本轮探针在开发栈留有 `probe*` 前缀的 7 个用户、1 张 1×1 PNG 媒体与 1 条私密笔记（noteId=188，标题"私密探针"）。开发库为随机化测试数据，不做清理；若需清理按媒体删除闭环接口或库内软删处理。
- **临时脚本边界**：探针脚本位于系统临时目录，验证后已删除，不入库；P15-06 会将其逻辑正规化为可重复执行的黑盒脚本。

## 7. 勘误（2026-09-12，P15 施工时回溯）

- 探针 2 的原始结论"注册接口无频率限制"**不成立**：`AuthService.enforceRegisterRateLimit` 自 MVP 起即存在（20 次/IP/小时），探针仅发送 6 次未达阈值，证据不足以支撑该结论。修正后的发现是 **IP 归因缺陷**（nginx 未传 XFF + 后端取 XFF 首段/远端地址，见 §3 探针 2 修正行），P15-01 据此实施：nginx 传递 `$proxy_add_x_forwarded_for`、后端取 XFF 末段防伪造、限流阈值可配置化并扩展至发笔记/社交/上传写接口。
