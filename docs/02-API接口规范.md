# Scenary 02 · API 接口规范（MVP 契约）

> 本文档是前后端并行开发的**唯一契约**。实现以本文为准；不一致时改代码不改文档，改文档必须记录变更。
> 配套：架构背景见 [01](01-技术栈与总体架构.md)，施工顺序见 [03](03-MVP实施与Docker部署.md)。
> 版本 v1.6 · 2026-09-11

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
| 40300 | 403 | 无权操作该资源 | 删除别人的笔记；注销账号时密码二次确认失败 |
| 40301 | 403 | 账号已被禁用 | users.status=0 登录；users.status=2（已注销）登录同码，message 一致 |
| 40400 | 404 | 资源不存在 | 笔记已删除/私密被他人访问；用户主页已被注销 |
| 40901 | 409 | 媒体尚未处理完成 | 提交笔记时含 status≠1 的图或视频 |
| 40902 | 409 | 上传会话已过期 | 分片会话超过有效期后继续取片或合并 |
| 40903 | 409 | 上传分片不完整 | 合并时缺少分片、分片大小或总大小不符 |
| 41001 | 422 | 用户名已存在 | 注册重名 |
| 42001 | 429 | 请求过于频繁 | 登录锁；注册/发笔记/点赞/收藏/关注/上传等写接口触发 §1.4 限流，message 含剩余秒数 |
| 50000 | 500 | 服务器内部错误 | 兜底，message 固定"服务开小差了"不泄内部信息 |

### 1.3 游标分页约定

翻页类接口入参 `cursor`（Long，上一页返回的 `nextCursor`；首页不传）+ `limit`（默认 10，最大 20）。响应统一：

```json
"list": [ ...卡片/条目... ], "nextCursor": 183746, "hasMore": true
```

`hasMore=false` 时 `nextCursor=null`。

### 1.4 写接口限流约定（P15）

写接口按用户（或注册按客户端 IP）在 Redis 滑动窗口内限频，超出返回 `42001/429`，message 含剩余秒数。阈值经 `SCENARY_RATELIMIT_*` 环境变量可调；来自回环地址（127.0.0.1/::1）的客户端豁免注册限流，用于本地开发与自动化测试，生产流量经反代进入时携带真实客户端 IP 不受影响。

| 接口 | 限制 | key 维度 |
|---|---|---|
| POST /auth/register | 5 次/小时 | 客户端 IP |
| POST /notes | 30 次/10 分钟 | 用户 |
| PUT/DELETE /notes/{id}/like、/bookmark、/users/{id}/follow | 120 次/分钟 | 用户 |
| POST /media/images | 60 次/10 分钟 | 用户 |
| POST /notes/{id}/comments | 20 次/分钟（既有 P10 契约不变） | 用户 |

### 1.5 媒体 URL 生命周期（P12-E2）

对象存储桶默认**私有**（`mc anonymous set none`），所有响应中的媒体 URL 字段（`url`、`thumbUrl`、`coverUrl`、`avatarUrl`、`playbackUrl`、`playbackLowUrl`）均为**运行时生成的短时预签名 GET URL**（默认有效期 300 秒，`SCENARY_MEDIA_PRESIGN_TTL_SECONDS` 可调），非持久化直链：

- 数据库与缓存（feed 两级缓存、`note:card:{id}`）只持久化 object key；签名在 VO 组装时完成（本地 HMAC 计算，无网络 IO）。
- URL 带自校验签名：改动路径、参数或过期后由对象存储直接拒绝（403），客户端不得解析、拼接或长期缓存 URL；页面长时间停留后应由接口重新获取。
- 视频播放支持 Range 请求（拖动进度条），签名 URL 对 Range 同样有效。
- 回滚模式：`SCENARY_MEDIA_PRESIGN_READ=false` 且桶策略切回 public-read（`ops/set-bucket-policy.ps1 -Policy download`）时，读路径退回持久化直链。
- 历史直链数据经 `ops/migrate-media-urls.ps1 -ToKeys` 回填为 key（先 `-Preview`）。

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

### 3.8 DELETE /users/me — 注销账号 🔒（P15）

请求：`{ "password": "当前密码" }`。密码二次确认失败返回 40300。

服务端行为（同一事务 + 提交后吊销令牌）：

1. `users.status` 置 2（注销态），nickname 置"已注销用户"，bio 清空，头像对象从桶删除并清空 `avatar_url`；
2. 名下全部笔记软删（visibility=2 + deleted_at），全部评论软删（deleted_at，前端按既有"已删除"占位渲染）；
3. 访问/刷新令牌即时吊销，后续任何请求返回 40100，再次登录返回 40301（与禁用账号同码同文案，不区分提示）；
4. `username` 保留占用防冒名；点赞/收藏/关注关系与既有通知保留（计数语义与禁用账号一致）；
5. GET /users/{userId} 对注销用户返回 40400；feed/搜索/详情按既有可见性过滤自然消失。

响应 data：`{ "deactivated": true }`。本操作不可由 API 撤销；恢复属于运维动作（重置 users.status）。



免认证可看。响应 data = 3.1 结构去掉 username/email 类敏感字段，保留 id/nickname/avatarUrl/bio/noteCount/createdAt，并增加 P9 `social` 社交状态；匿名视角的 `following` 为 false，但关注/被关注计数仍公开返回。
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

### 3.6 PUT/DELETE /users/{userId}/follow — 关注/取消关注 🔒

PUT 和 DELETE 均幂等。不能关注自己；不存在、已删除或已禁用的用户统一返回 40400。响应返回统一社交状态：

```json
{
  "following": true,
  "followerCount": 18,
  "followingCount": 6,
  "liked": false,
  "bookmarked": false,
  "likeCount": 0,
  "bookmarkCount": 0
}
```

### 3.7 GET /users/me/bookmarks — 我的收藏 🔒

游标分页，参数为 `cursor` 和 `limit`（默认 10，最大 20），按收藏时间倒序。仅返回当前仍公开且未删除的笔记；取消收藏或笔记删除后不再出现在列表中。

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

### 4.3 POST /media/videos — 上传视频 🔒

multipart/form-data，字段名固定 `file`，一次一个视频；上限 200MB，服务端按容器格式/魔数识别 MP4/MOV/WEBM，忽略扩展名和客户端 MIME。当前基础链路限制时长 ≤120 秒、分辨率最长边 ≤3840，超过限制返回 `40000`。

流程：写入私有原始对象 → 建立 `mediaType=VIDEO`、`status=11 PROCESSING` 行 → 发布独立 `video.transcode` 消息 → 立即返回。消费者使用 `ffprobe/ffmpeg` 生成 JPEG 封面和 480p/720p MP4；原始视频不出现在响应中。视频状态码为 `10 UPLOADING`、`11 PROCESSING`、`12 READY`、`13 FAILED`、`14 EXPIRED`，不能与图片的 `0/1/2` 混用。

响应 data：

```json
{
  "mediaId": 601, "mediaType": "VIDEO", "status": 11,
  "url": "http://.../thumb/202609/video_t.jpg", "thumbUrl": null,
  "width": null, "height": null, "durationMs": null,
  "playbackUrl": null, "playbackLowUrl": null
}
```

处理完成后 `GET /media/{mediaId}` 返回 `status=12`、封面 `url/thumbUrl`、`durationMs`、`playbackUrl`（720p）和 `playbackLowUrl`（480p）；失败为 `status=13`，前端显示失败并允许删除后重传。视频队列与图片缩略图队列隔离，失败消息进入视频专用 DLQ。

### 4.4 DELETE /media/{mediaId} — 删除未使用的媒体 🔒 (仅 owner)

仅允许 note_id IS NULL 的游离媒体删除（发布绑定后走删笔记通道）。删除接口保留兼容的异步清理语义；后台定时任务会在保留期后删除对象并清理数据库行。响应 data=null。

### 4.5 POST /media/video-uploads — 创建视频分片上传会话 🔒

请求体：

```json
{ "fileName": "valley.mp4", "sizeBytes": 52428800, "mime": "video/mp4" }
```

服务端固定使用 8MiB 分片，按 `sizeBytes` 计算 `totalParts`，总大小仍不得超过 200MB；`fileName` 和客户端 MIME 只用于展示/提示，不参与容器信任。会话有效期 2 小时，状态为 `0 UPLOADING`、`1 MERGING`、`2 COMPLETED`、`3 EXPIRED`。响应不包含原始对象 key：

```json
{
  "uploadId": "uuid",
  "chunkSize": 8388608,
  "totalParts": 7,
  "status": 0,
  "expiresAt": 1788500000000,
  "uploadedParts": [{ "partNumber": 1, "sizeBytes": 8388608 }]
}
```

### 4.6 GET /media/video-uploads/{uploadId} — 查询会话与已上传分片 🔒 (仅 owner)

返回 4.5 同形态。服务端逐片核对对象存储中的实际大小，前端刷新后重新选择同一 `fileName + sizeBytes + lastModified` 的文件即可跳过已完成分片；浏览器不持久化文件内容。

### 4.7 POST /media/video-uploads/{uploadId}/parts/{partNumber}/url — 获取分片预签名 PUT 🔒

`partNumber` 为 1~`totalParts`。服务端仅为当前 owner、未过期且未完成会话签发短时（15 分钟）PUT URL，对象 key 仅落在会话专属前缀。浏览器直接向 URL PUT 分片，不携带 access token；URL 过期后重新调用本接口即可。

### 4.8 POST /media/video-uploads/{uploadId}/complete — 合并并校验视频 🔒

无请求体。服务端核对所有分片存在、前 `n-1` 片为 8MiB、末片为期望大小且总大小等于会话声明值，随后在 MinIO 服务端 compose/copy 为原始对象，重新按 MP4/MOV 的 ISO-BMFF `ftyp` 或 WebM EBML 魔数识别容器，创建 `media(status=11)` 并投递独立转码队列。响应为 4.3 的 `MediaItemVO`；重复 complete 返回同一 `mediaId`，不会重复发布。

合并前校验不通过返回 40903；会话过期返回 40902。合并失败不会丢弃已上传分片，仍可补片后重试；定时任务清理过期会话及其分片对象。

### 4.9 DELETE /media/video-uploads/{uploadId} — 取消分片上传会话 🔒

仅允许 owner 取消未完成会话；服务端删除分片对象和会话记录，响应 data=null。发布页离开时默认保留会话供刷新恢复，不自动调用取消。

---

## 5. 笔记模块 /notes

### 5.1 POST /notes — 发布笔记 🔒

请求体：

```json
{
  "title": "雨后的四姑娘山",
  "content": "十月初的雪线，云开了一小时。拍摄于双桥沟。",
  "placeName": "四川·四姑娘山",
  "latitude": 30.9785,
  "longitude": 102.7591,
  "placeSource": "MAP",
  "placePrecision": "EXACT",
  "mediaIds": [501, 502, 503],
  "requestKey": "7d4a0b4b-0e06-4cbe-a7d6-f4d1f1b2a6f0"
}
```

校验：title 1~64 必填；content ≤2000 可空串；mediaIds 1~9 个、全部属于本人、图片 status=1 或视频 status=12（否则 40901）；placeName ≤128 可空；latitude 范围 -90~90、longitude 范围 -180~180，必须成对出现；placeSource 取 `MANUAL`、`EXIF`、`MAP`，缺省按来源推断；placePrecision ≤32。EXIF 坐标只在服务端读取，公开响应不返回原始 EXIF 坐标，只有用户明确选择 MAP/分享坐标时才返回 latitude/longitude；requestKey 可选，建议 UUID，同一 user+requestKey 重试返回同一篇笔记。
事务动作：insert notes → 批量 update media SET note_id, order_no(1..n) → 回填 notes.cover_url=首图 thumb_url、media_count → 事务提交后由 Controller 推进 Redis `feed:first:v1:version`，避免发布响应前的旧查询回写为当前首页快照。

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
  "latitude": 30.9785,
  "longitude": 102.7591,
  "placeSource": "MAP",
  "placePrecision": "EXACT",
  "visibility": 1,
  "createdAt": 1756261200000,
  "author": {
    "id": 10086, "nickname": "山野行人",
    "avatarUrl": "http://.../avatar/10086/a1b2.jpg"
  },
  "images": [
    { "mediaId": 501, "mediaType": "IMAGE", "url": "http://.../thumb/202608/a1_t.jpg",
      "thumbUrl": "http://.../thumb/202608/a1_t.jpg", "width": 1080, "height": 1440,
      "durationMs": null, "playbackUrl": null, "playbackLowUrl": null },
    { "mediaId": 502, "url": "...", "thumbUrl": "...", "width": 1080, "height": 810 }
  ],
  "social": {
    "liked": false,
    "bookmarked": false,
    "following": false,
    "likeCount": 12,
    "bookmarkCount": 5,
    "followerCount": 18,
    "followingCount": 6
  },
  "mine": false
}
```

说明：列表场景只用 coverUrl/thumbUrl；详情页图片用 `url`，视频用 `url` 作封面并以 `playbackUrl/playbackLowUrl` 播放；原始视频 object key 不返回。`latitude/longitude` 仅对用户明确选择分享的地点返回，EXIF 来源不返回原始坐标。

### 5.3 DELETE /notes/{id} — 删除笔记 🔒 (仅 author)

软删：visibility=2 + deleted_at；DEL feed 缓存与 `note:card:{id}`。响应 data=null。幂等（重复删除仍 0）。

不做编辑接口（MVP 边界）：想改内容=删除重发。

### 5.4 PUT/DELETE /notes/{id}/like — 点赞/取消点赞 🔒

仅允许公开且未删除的笔记。两次 PUT 不增加计数，两次 DELETE 不报错。响应为详情中的 `social` 对象。

### 5.5 PUT/DELETE /notes/{id}/bookmark — 收藏/取消收藏 🔒

仅允许公开且未删除的笔记，幂等语义同点赞。匿名详情不返回用户私有关系状态，统一返回 `liked=false`、`bookmarked=false`、`following=false`，但公开计数仍返回。

### 5.6 GET /notes/{id}/comments — 评论列表

免认证可看公开笔记的评论；私密笔记仅作者可看，其他情况返回 40400。Query：`cursor`（缺省从最早评论开始，传上一页 `nextCursor` 后取更大的 id）、`limit`（默认 10，最大 20）。按 `id ASC` 固定正序，已删除评论保留为占位。

响应 data：

```json
{
  "list": [
    {
      "id": 701, "noteId": 901, "parentId": null,
      "content": "云层打开的瞬间太美了", "status": 1,
      "author": { "id": 10087, "nickname": "山间来客", "avatarUrl": null },
      "createdAt": 1756261300000, "mine": false, "canDelete": false
    }
  ],
  "nextCursor": 701, "hasMore": true
}
```

`status=2` 时 `content` 固定为“该评论已删除”，前端保留评论位置；作者只能删除自己的评论，删除为软删且幂等。一级评论最多 500 字，回复最多 300 字；正文按纯文本处理，服务端拒绝 HTML/脚本片段、控制字符、配置的敏感词和超过 20 条/分钟的评论写入。敏感词命中统一返回 `40000`，不回显具体命中词；词表由部署环境变量 `SCENARY_COMMENT_SENSITIVE_WORDS` 以逗号分隔提供。

### 5.7 POST /notes/{id}/comments — 创建评论或回复 🔒

请求体：`{ "content": "评论内容", "parentId": null }`。`parentId` 缺省或为 null 创建一级评论；不为 null 时必须指向同一笔记下的未删除评论。成功返回上述评论条目。创建评论、点赞和关注会为目标用户生成站内通知；操作者对自己的笔记/账号执行动作不生成自通知。

### 5.8 DELETE /comments/{id} — 软删除评论 🔒

仅评论作者可操作（当前无管理员角色）；只能删除自己的评论，重复删除返回成功，其他作者返回 40300，不存在返回 40400。

---

## 5.9 通知模块 /notifications

### GET /notifications — 当前用户通知 🔒

Query：`cursor`（缺省取最新，传上一页 `nextCursor` 后取更小的 id）、`limit`（默认 10，最大 20），按 `id DESC` 固定倒序。响应在游标分页字段外增加 `unreadCount`；通知不因关联笔记/评论软删而消失。

```json
{
  "list": [
    {
      "id": 801, "type": "COMMENT", "actor": { "id": 10087, "nickname": "山间来客", "avatarUrl": null },
      "noteId": 901, "commentId": 701, "noteTitle": "雨后的四姑娘山",
      "commentPreview": "云层打开的瞬间太美了", "readAt": null,
      "createdAt": 1756261300000
    }
  ],
  "nextCursor": 801, "hasMore": true, "unreadCount": 3
}
```

`type` 当前取 `LIKE`、`FOLLOW`、`COMMENT`、`REPLY`。`readAt` 非 null 表示已读。

### POST /notifications/read 🔒

请求体：`{ "ids": [801, 802] }`；只更新当前用户拥有的通知，重复调用幂等。`ids` 为空数组表示将当前用户全部未读通知标记为已读，最多一次提交 100 个指定 id。成功返回 `data=null`。

### 5.10 WebSocket /api/v1/ws/notifications

🔒 通知实时推送为增强通道，HTTP 分页接口仍是唯一可靠数据源。客户端连接后必须先发送认证帧，服务端不接受 URL query 中的令牌：

```json
{ "type": "AUTH", "accessToken": "<ACCESS_TOKEN>" }
```

认证成功返回 `{ "type": "READY" }`。评论、点赞或关注事务提交后，若当前用户在线，服务端发送：

```json
{ "type": "NOTIFICATION", "unreadCount": 3 }
```

客户端收到后重新请求 `GET /notifications?limit=1` 获取最新通知和准确未读数。令牌无效、账号禁用或首帧不是 `AUTH` 时关闭连接；连接失败、断线或服务端不支持 WebSocket 时，前端继续使用 30 秒轮询，不阻塞主页面。

---

## 6. 搜索模块 /search

### 6.1 GET /search/notes — 搜索公开笔记

免认证。Query：`q` 必填；`cursor` 可选 opaque 字符串；`limit` 默认 10、最大 20；`sort` 可选 `recent`（默认）或 `relevance`。

服务端先对 `q` 去除首尾空白；空值或长度不足 2（按 Java 字符数）返回 `40000`，超过 64 返回 `40000`。搜索词按字面匹配，`%`、`_` 和反斜杠不会获得 LIKE 通配语义。搜索范围为标题、正文、地点名和作者昵称。

只返回 `notes.visibility=1`、`users.status=1` 的结果；已删除笔记、私密笔记和禁用作者的笔记均不可见。`recent` 按 `createdAt DESC, id DESC`；`relevance` 按标题命中 4 分、地点名命中 3 分、作者昵称命中 2 分、正文命中 1 分，再按 `createdAt DESC, id DESC`。排序稳定键由服务端 opaque cursor 携带，cursor 绑定原始规范化 query 与 sort，不得跨 query/sort 复用；非法 cursor 返回 `40000`。

响应 data：

```json
{
  "list": [
    {
      "id": 901,
      "title": "月下宫墙",
      "contentPreview": "夜色落在旧城墙上……",
      "coverUrl": "http://.../thumb/202609/wall_t.jpg",
      "coverWidth": 1080,
      "coverHeight": 1440,
      "mediaCount": 1,
      "author": { "id": 10086, "nickname": "山野行人", "avatarUrl": null },
      "createdAt": 1756261200000,
      "social": {
        "liked": false, "bookmarked": false, "following": false,
        "likeCount": 0, "bookmarkCount": 0, "followerCount": 0, "followingCount": 0
      },
      "highlight": {
        "title": "月下宫墙",
        "content": null,
        "placeName": "四川·旧城墙",
        "author": null
      }
    }
  ],
  "nextCursor": "eyJ...",
  "hasMore": false
}
```

`highlight` 为可选纯文本摘录对象，字段未命中时为 null，不包含 HTML 标签；前端必须按文本渲染。搜索结果与 Feed 卡片字段同构，但不复用 Feed 首页缓存。

---

## 7. Feed 模块 /feed

### 6.1 GET /feed — 双列瀑布流数据源

免认证。Query：`cursor`(long，首页缺省)、`limit`(默认 10 ≤20)。

语义：全站 visibility=1，按 id DESC。第一页公共卡片结果整页缓存 Redis(TTL 300s)，任何发布/删除操作会使缓存版本失效；登录视角的 `social` 状态在返回前按当前用户重新聚合，不写入共享缓存。发布后首页允许极短的缓存/请求调度最终一致窗口，详情接口可立即读取已提交笔记。

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
      "createdAt": 1756261200000,
      "social": {
        "liked": false, "bookmarked": false, "following": false,
        "likeCount": 12, "bookmarkCount": 5,
        "followerCount": 18, "followingCount": 6
      }
    }
  ],
  "nextCursor": 901,
  "hasMore": true
}
```

约定：`coverWidth/coverHeight` 取自 media 宽高，供瀑布流预占位防抖动；无值则前端默认 3:4。
`contentPreview` 后端截断前 48 字符+"…"。

---

## 8. 系统

| 端点 | 说明 |
|---|---|
| GET /actuator/health | compose healthcheck 用，暴露 health 即可 |
| GET /api/v1/ping | 连通性自测：`{"code":0,"data":{"pong":"v1"}}`（注意不走 /api/v1 base 时 nginx 直透） |

---

## 9. 关键交互时序（前端实现对照）

**令牌过期自愈**：任意请求收 401 → axios 拦截器用 Pinia 里 refreshToken 调 `/auth/refresh` → 成功则替换双令牌并**重放原请求**；失败（40101）→ 清空本地态跳 `/login?redirect=`。

**发布页上传循环**：
选文件(客户端先行校验张数/大小/mime) → 视频创建/恢复会话 → 逐片取预签名 URL 并直传（每片成功后持久化会话进度）→ complete 合并校验 → 拿到 mediaId 后每 800ms 轮询状态聚合（全 1/12→可提交；任一失败→标记失败卡片允许重传）→ 提交 POST /notes → router.push('/')。图片仍走原有代理上传。

**访问控制路由表**：

| 前端路由 | 鉴权 |
|---|---|
| /login | 已登录则跳 / |
| / | 公开 |
| /note/:id | 公开（私密会收到 404 展示） |
| /publish | 必须登录（守卫重定向 login?redirect=/publish） |
| /user/:id | 公开；id==me 时显示编辑入口 |
| /notifications | 必须登录；通知轮询失败不阻塞其他页面 |

---

## 9. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-08-27 | 初版：18 个端点定稿 |
| v1.1 | 2026-09-04 | P9：新增点赞、收藏、关注、我的收藏；详情/Feed/主页增加社交状态与计数；V4 关系表迁移 |
| v1.2 | 2026-09-04 | P10：新增评论/回复、软删除、通知分页和批量已读；V5 评论/通知表迁移 |
| v1.3 | 2026-09-04 | P11：新增公开笔记搜索、recent/relevance 排序、opaque cursor、纯文本 highlight；V6 搜索读路径索引 |
| v1.4 | 2026-09-04 | P12：新增视频上传/状态/播放字段、独立转码队列约定和笔记坐标字段；V7 媒体/地点扩展 |
| v1.5 | 2026-09-04 | P12-E1：新增视频分片上传会话、预签名 PUT、断点恢复、合并校验、取消与过期清理；V8 会话/分片表 |
| v1.6 | 2026-09-11 | P15：新增 DELETE /users/me 注销账号；新增 §1.4 写接口限流约定（注册按 IP、写接口按用户，42001/429）；users.status 增加 2=注销态语义，注销登录并入 40301，注销用户主页 40400；通知保留策略（已读超期定时清理） |
