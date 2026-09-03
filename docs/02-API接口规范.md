# Scenary 02 · API 接口规范（MVP 契约）

> 本文档是前后端并行开发的**唯一契约**。实现以本文为准；不一致时改代码不改文档，改文档必须记录变更。
> 配套：架构背景见 [01](01-技术栈与总体架构.md)，施工顺序见 [03](03-MVP实施与Docker部署.md)。
> 版本 v1.0 · 2026-08-27

---

## 1. 全局约定

| 项 | 约定 |
|---|---|
| Base URL | 生产 `/api/v1`（nginx 同源反代）；开发 Vite proxy `/api` → `http://localhost:8080/api/v1` |
| 传输格式 | 请求/响应均 `application/json; charset=utf-8`（multipart 上传除外） |
| 认证方式 | 请求头 `Authorization: Bearer <accessToken>`；无状态 JWT |
| 时间字段 | 统一 **epoch 毫秒时间戳（Long）**，UTC 存储、前端本地渲染（规避时区坑） |
| ID 字段 | 全部为 Long 数字 id |
| 字段命名 | JSON 驼峰；空对象返回 `{}` 不返回 null |

### 1.1 统一响应包络

所有接口 HTTP 状态码只表示传输层语义（200=业务已受理；401=未认证；403/404 由包络内 code 表达也可直接用 HTTP 同码——本契约取**HTTP 与业务码一致**策略便于拦截器处理）：

```json
{ "code": 0, "message": "ok", "data": { } }
```

失败示例：

```json
{ "code": 40901, "message": "媒体仍在处理中，请稍后重试", "data": null }
```

### 1.2 错误码表

| code | HTTP | 含义 | 触发场景举例 |
|---|---|---|---|
| 0 | 200 | 成功 | — |
| 40000 | 400 | 参数校验失败 | 标题超长、密码强度不足、图片数量>9 |
| 40100 | 401 | 未登录 / 缺少令牌 | 未带 Authorization 访问受保护接口 |
| 40101 | 401 | 令牌无效或已过期 | access 过期且 refresh 失败 |
| 40300 | 403 | 无权操作该资源 | 删除别人的笔记 |
| 40301 | 403 | 账号已被禁用 | users.status=0 登录 |
| 40400 | 404 | 资源不存在 | 笔记已删除/私密被他人访问 |
| 40901 | 409 | 媒体尚未处理完成 | 提交笔记时含 status≠1 的图 |
| 41001 | 422 | 用户名已存在 | 注册重名 |
| 42001 | 429 | 请求过于频繁 | 触发登录锁/注册限流，message 含剩余秒数 |
| 50000 | 500 | 服务器内部错误 | 兜底，message 固定"服务开小差了"不泄内部信息 |

### 1.3 游标分页约定

翻页类接口入参 `cursor`（Long，上一页返回的 `nextCursor`；首页不传）+ `limit`（默认 10，最大 20）。响应统一：

```json
"list": [ ...卡片/条目... ], "nextCursor": 183746, "hasMore": true
```

`hasMore=false` 时 `nextCursor=null`。

---

## 2. 认证模块 /auth

### 2.1 POST /auth/register — 注册

免认证。成功即视为登录（直接发双令牌）。

请求体：

```json
{ "username": "hill_walker", "password": "Str0ngPass!", "nickname": "山野行人" }
```

校验规则：username 匹配 `^[a-zA-Z0-9_]{4,20}$` 且全局唯一；password 长度 8~64 且至少含字母+数字；nickname 可选(1~32)，缺省用 username。

响应 data：

```json
{
  "userId": 10086,
  "username": "hill_walker",
  "nickname": "山野行人",
  "avatarUrl": null,
  "accessToken": "eyJhbGciOi...",
  "accessExpiresIn": 7200,
  "refreshToken": "eyJhbGciOi...",
  "refreshExpiresIn": 2592000
}
```

错误：40000 校验失败 / 41001 重名 / 42001 IP 触发注册限流（20 次/IP/小时）。

### 2.2 POST /auth/login — 登录

请求：`{ "username": "...", "password": "..." }`

响应 data：与注册同构。连续错 5 次/账号+IP → 42001 锁定 15 分钟；status=0 → 40301。

### 2.3 POST /auth/refresh — 刷新令牌（旋转）

免认证。请求：`{ "refreshToken": "eyJhbGciOi..." }`
行为：验签 + Redis 白名单命中 → **删除旧 jti**，签发全新一对（响应同登录）。白名单未命中（已旋转/已登出）→ 40101。

### 2.4 POST /auth/logout — 登出 🔒

需认证。作用：吊销当前 refreshToken 白名单并把本次 access jti 拉黑至其自然过期。响应 `{"code":0,"data":null}`。幂等。

🔒 = 需要 Bearer 认证的接口，下同。

---

## 3. 用户模块 /users

### 3.1 GET /users/me — 我的信息 🔒

响应 data：

```json
{
  "id": 10086, "username": "hill_walker", "nickname": "山野行人",
  "avatarUrl": "http://localhost:9000/scenary-media/avatar/10086/a1b2.jpg",
  "bio": "只拍山和海", "noteCount": 12,
  "createdAt": 1756272000000
}
```

### 3.2 PATCH /users/me — 编辑资料 🔒

请求（均可选，至少一项）：`{ "nickname": "新昵称", "bio": "新签名" }`
校验：nickname 1~32、bio ≤200。响应：更新后的完整 me 对象（同 3.1）。

### 3.3 POST /users/me/avatar — 上传头像 🔒

Content-Type: multipart/form-data，字段名 `file`，单张 image/jpeg|png ≤5MB。
同步处理：MinIO 存 `avatar/{userId}/{uuid}.jpg` + Thumbnailator 生成 200×200 封面式缩放覆盖原路径（头像不分图）。
响应 data：`{ "avatarUrl": "http://.../avatar/10086/x.jpg" }`，并自动写入 users.avatar_url。

### 3.4 GET /users/{userId} — 用户公开主页

免认证可看。响应 data = 3.1 结构去掉 username/email 类敏感字段，保留 id/nickname/avatarUrl/bio/noteCount/createdAt。
（访问自己时可通过 3.1 判断身份。）

### 3.5 GET /users/{userId}/notes — TA 的笔记网格

游标分页。仅返回 visibility=1 公开笔记；若带认证且 userId==自己，则附加私密笔记（按 updated_at 序也并入 id 游标即可，MVP 简化为 id 游标混排）。

响应 list 元素（NoteCardVO）：

```json
{
  "id": 901, "title": "雨后的四姑娘山", "coverUrl": "http://.../thumb/202608/x_t.jpg",
  "mediaCount": 5, "visibility": 1, "createdAt": 1756261200000
}
```

---

## 4. 媒体模块 /media

### 4.1 POST /media/images — 批量上传原图 🔒

multipart/form-data，字段名 `files`，可重复多个，1≤数量≤9；单张 ≤10MB；mime 限 jpeg/png/gif。
流程：写 MinIO 原图 → 建 media(status=0) → 发 MQ → **立即返回**。

> 当前运行时不引入 WebP 解码器，因此 WebP 即使魔数正确也在上传阶段以 `40000` 拒绝；这样不会先存原图再进入不可恢复的异步失败。后续若登记并引入稳定解码器，需先更新本契约和依赖评估。

响应 data：

```json
{
  "items": [
    { "mediaId": 501, "url": "http://.../thumb/202608/a1_t.jpg", "thumbUrl": null,
      "status": 0, "width": null, "height": null }
  ]
}
```

> 注：上文示例 URL 为展示地址；生产/容器部署时 host 由后端配置 `SCENARY_MINIO_PUBLIC_HOST` 决定（可能指向反代路径，如 `http://<服务器>:8081/minio`）。默认策略只返回缩略图展示地址，即使仍在处理中也不把原图直链交给客户端；只有显式打开 `SCENARY_MINIO_EXPOSE_ORIGINAL_URL=true` 才返回原图。**前端铁律：永远直接使用接口返回的完整 URL，禁止自行拼接域名或改写路径。**

### 4.2 GET /media/{mediaId} — 查询处理状态 🔒 (仅 owner)

用于上传后的轮询（建议间隔 800ms，最多 30 次，超时当失败展示重试按钮）。

响应 data（完成态）：

```json
{ "mediaId": 501, "status": 1,
  "url": "http://.../thumb/202608/a1_t.jpg", "thumbUrl": "http://.../thumb/202608/a1_t.jpg",
  "width": 1080, "height": 1440 }
```

说明：`url` 为展示 URL；默认返回缩略图展示地址，是否暴露原图由 `SCENARY_MINIO_EXPOSE_ORIGINAL_URL` 决定。处理失败时 `status=2`，前端应立即显示失败并允许重传。

状态机：`0 PROCESSING → 1 DONE / 2 FAILED`；消费者本地重试耗尽后先写入 `status=2` 与失败原因/时间，再 `nack(requeue=false)` 进入 DLQ，前端可立即展示失败并允许重传。
错误：40300 非 owner；40400 不存在。

### 4.3 DELETE /media/{mediaId} — 删除未使用的媒体 🔒 (仅 owner)

仅允许 note_id IS NULL 的游离媒体删除（发布绑定后走删笔记通道）。删除接口保留兼容的异步清理语义；后台定时任务会在保留期后删除对象并清理数据库行。响应 data=null。

---

## 5. 笔记模块 /notes

### 5.1 POST /notes — 发布笔记 🔒

请求体：

```json
{
  "title": "雨后的四姑娘山",
  "content": "十月初的雪线，云开了一小时。拍摄于双桥沟。",
  "placeName": "四川·四姑娘山",
  "mediaIds": [501, 502, 503],
  "requestKey": "7d4a0b4b-0e06-4cbe-a7d6-f4d1f1b2a6f0"
}
```

校验：title 1~64 必填；content ≤2000 可空串；mediaIds 1~9 个、全部属于本人、全部 status=1（否则 40901）；placeName ≤128 可空；requestKey 可选，建议 UUID，同一 user+requestKey 重试返回同一篇笔记。
事务动作：insert notes → 批量 update media SET note_id, order_no(1..n) → 回填 notes.cover_url=首图 thumb_url、media_count → 推进 Redis `feed:first:v1:version`（提交后再次推进，隔离并发旧查询回写）。

响应 data：

```json
{ "id": 901, "coverUrl": "http://.../thumb/202608/a1_t.jpg" }
```

### 5.2 GET /notes/{id} — 笔记详情

免认证可看公开笔记；私密仅本人（带 token）可看否则 40400（对他人隐藏存在性）。

响应 data：

```json
{
  "id": 901,
  "title": "雨后的四姑娘山",
  "content": "十月初的雪线……",
  "placeName": "四川·四姑娘山",
  "visibility": 1,
  "createdAt": 1756261200000,
  "author": {
    "id": 10086, "nickname": "山野行人",
    "avatarUrl": "http://.../avatar/10086/a1b2.jpg"
  },
  "images": [
    { "mediaId": 501, "url": "http://.../thumb/202608/a1_t.jpg",
      "thumbUrl": "http://.../thumb/202608/a1_t.jpg", "width": 1080, "height": 1440 },
    { "mediaId": 502, "url": "...", "thumbUrl": "...", "width": 1080, "height": 810 }
  ],
  "mine": false
}
```

说明：列表场景只用 coverUrl/thumbUrl；详情页 img 用返回的 url（默认缩略图展示，是否暴露原图由 `SCENARY_MINIO_EXPOSE_ORIGINAL_URL` 决定）。

### 5.3 DELETE /notes/{id} — 删除笔记 🔒 (仅 author)

软删：visibility=2 + deleted_at；DEL feed 缓存与 `note:card:{id}`。响应 data=null。幂等（重复删除仍 0）。

不做编辑接口（MVP 边界）：想改内容=删除重发。

---

## 6. Feed 模块 /feed

### 6.1 GET /feed — 双列瀑布流数据源

免认证。Query：`cursor`(long，首页缺省)、`limit`(默认 10 ≤20)。

语义：全站 visibility=1，按 id DESC。第一页结果整页缓存 Redis(TTL 300s)，任何发布/删除操作会使缓存失效。

响应 data：

```json
{
  "list": [
    {
      "id": 901, "title": "雨后的四姑娘山",
      "contentPreview": "十月初的雪线，云开了一小时…",
      "coverUrl": "http://.../thumb/202608/a1_t.jpg",
      "coverWidth": 1080, "coverHeight": 1440,
      "mediaCount": 5,
      "author": { "id": 10086, "nickname": "山野行人", "avatarUrl": "http://..." },
      "createdAt": 1756261200000
    }
  ],
  "nextCursor": 901,
  "hasMore": true
}
```

约定：`coverWidth/coverHeight` 取自 media 宽高，供瀑布流预占位防抖动；无值则前端默认 3:4。
`contentPreview` 后端截断前 48 字符+"…"。

---

## 7. 系统

| 端点 | 说明 |
|---|---|
| GET /actuator/health | compose healthcheck 用，暴露 health 即可 |
| GET /api/v1/ping | 连通性自测：`{"code":0,"data":{"pong":"v1"}}`（注意不走 /api/v1 base 时 nginx 直透） |

---

## 8. 关键交互时序（前端实现对照）

**令牌过期自愈**：任意请求收 401 → axios 拦截器用 Pinia 里 refreshToken 调 `/auth/refresh` → 成功则替换双令牌并**重放原请求**；失败（40101）→ 清空本地态跳 `/login?redirect=`。

**发布页上传循环**：
选文件(客户端先行校验张数/大小/mime) → 并发度 3 逐张 POST /media/images → 全部拿到 mediaId 后每 800ms 轮询状态聚合（全 1→可提交；任一 2→标记失败卡片允许移除重传）→ 提交 POST /notes → router.push('/')。

**访问控制路由表**：

| 前端路由 | 鉴权 |
|---|---|
| /login | 已登录则跳 / |
| / | 公开 |
| /note/:id | 公开（私密会收到 404 展示） |
| /publish | 必须登录（守卫重定向 login?redirect=/publish） |
| /user/:id | 公开；id==me 时显示编辑入口 |

---

## 9. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-27 | 初版：18 个端点定稿 |
