# Scenary · 交接文档（HANDOVER）

> 新会话/新协作者快速接管用。治理规则入口仍是 [../AGENTS.md](../AGENTS.md)，本文只回答"现状怎么跑、测什么、下一步做什么"。
> 更新时间：2026-09-30 · 对应版本 v2.48.2 · 进度真相源 [03 手册末尾 Checklist](03-MVP实施与Docker部署.md) · 最新验收证据 [E5](evidence/2026-09-28-E5地图浏览验收.md) / [E4](evidence/2026-09-19-P12-E4地图UI验收.md) / [E3](evidence/2026-09-19-P12-E3逆地理编码验收.md) / [P16](evidence/2026-09-19-P16夹缝任务包验收.md) / [P17](evidence/2026-09-19-P17修图与滤镜验收.md) / [P18](evidence/2026-09-19-P18社区治理验收.md) · 当前改进入口：已立项队列清空，下一步候选评估见 [07 缺口评估](07-功能缺口评估与后续候选.md)（提案）· 文档导航 [docs/README](README.md) · 最新学习笔记 [27](learning/27-CI真栈排障的三层陷阱.md)（CI 真栈排障三层陷阱） / [26](learning/26-E2E视频媒体夹具的三重坑.md) / [25](learning/25-WebKit路由拦截与E2E时序.md)

## 1. 一句话现状

后端 MVP 主链路已验收，前端七视图可用并已切换薄荷绿×青冥×茶白双主题；P4、P5、P6 和 Phase 7 均已验收。P8 已于 2026-09-03 通过全量出口门禁；P9、P10（含未覆盖项补充）、P11 搜索与发现和 P12 视频与地点（含 E1 分片上传）均已于 2026-09-04 完成。全栈由 Docker Compose 在 `:8081` 对外提供，backend health、`/api`、`/minio`、SPA history 和 WebSocket 升级链路已实测。2026-09-12：P15 账号与安全基线（限流/安全头/密码找回脚本/账号注销/通知保留）完成；**P12-E2 私有桶与短时签名完成——对象桶已私有，全部媒体 URL 为运行时短时签名（TTL 300s），"私密笔记封面匿名可达"漏洞已封死**。 2026-09-19：P15/E2 未提交工作落库（v2.40~v2.41）；06 功能拓展规划转正立项并完成 **P16 夹缝任务包**（笔记编辑 PUT /notes/{id}、关注者/正在关注列表、OpenAPI 生产默认关）、**P17 修图与滤镜**（前端裁剪/旋转/16 滤镜，flag 可摘除）与 **P18 社区治理最小闭环**（举报达阈值 5 自动隐藏 visibility=3、屏蔽双向过滤 feed/搜索/网格/详情、ops/list-reports 只读报表）；同日完成**文档结构重整**（docs/README 文档地图、07 缺口评估、archive 归档，v2.45）。**P12-E3 逆地理编码完成（v2.46）**：`GET /places/reverse-geocode`（02 契约 v1.9 §7B，登录用户、30/min 限流、geohash-5 缓存 30 天、provider 关闭/超时/失败一律空候选），place 包 provider SPI + 自托管 Nominatim（compose `geo` profile 默认不启动、无公网端口），发布页防抖候选联动（空字段回填/已填候选条确认）。**P12-E4 地图 UI 完成（v2.47）**：leaflet ^1.9.4（独立 chunk，`VITE_ENABLE_MAP=false` 整体摘除），发布页地图选点（点击/拖 marker → 坐标 → E3 候选联动，手工坐标输入保留为键盘路径）、详情页带坐标渲染只读小地图（瓦片失败降级坐标文本），CSP Report-Only 增补瓦片域。契约 02 v1.9。**E5 地图浏览完成（v2.48，2026-09-28）**：`GET /places/notes`（02 契约 v1.10，公开只读、屏蔽双向过滤、keyset 游标、V12 坐标复合索引）+ `/map` 公开路由（moveend 防抖视野框查询、marker 跳详情、空视野文案、加载更多）；**国内瓦片源决策**：默认高德矢量瓦片（GCJ-02），前端 `geoCoord.js` 做 WGS84↔GCJ-02 转换，`VITE_TILE_URL`/`VITE_TILE_GCJ02` 可覆盖；CI 浏览器 e2e 改跑真实 Compose 栈。契约 02 v1.10。

## 2. 如何跑起来

```bash
# 全栈验收/演示（唯一对外端口 :8081）
cd /d/Resume_project/scenary
docker compose up -d --build
docker compose ps
```

浏览器打开 http://localhost:8081 。`.env` 必须存在且不入库；丢失密钥按 Phase 0 重生成后更新 `.env`。本地开发仍可按 03 的 Phase 2/5 方式启动中间件、`backend/run-dev.sh` 和 Vite。

## 3. 测试矩阵（全部可重复执行）

| 层 | 命令 | 覆盖 | 当前状态 |
|---|---|---|---|
| 认证安全 | `node docs/dev/test-auth34.mjs` | 注册/旋转重放/登出吊销/登录锁 | ✅18 |
| 拦截链 | `node docs/dev/test-interceptor35.mjs` | 无令牌矩阵/黑名单/ThreadLocal 清理 | ✅8 |
| 上传管线 | `node docs/dev/test-media36.mjs` | 魔数欺骗/越权/删除闭环/对象可读/异步状态 | ✅15 |
| MQ 闭环 | `node docs/dev/test-thumbnail37.mjs` | 出片尺寸语义/DLQ 双毒消息；管理端口不可达时自动走 Compose 容器 | ✅7 |
| 端到端 | `node docs/dev/test-e2e38.mjs` | 发布/feed 缓存/可见性矩阵/软删幂等 | ✅30 |
| P8 Compose 集成 | `node docs/dev/test-p8-integration.mjs` | Flyway/约束、Redis 吊销、RabbitMQ 失败/DLQ、Feed 并发缓存 | ✅6 |
| P9 社交黑盒 | `node docs/dev/test-p9-social.mjs` | V4 关系、并发幂等、匿名/登录视图、收藏游标、可见性和禁用账号边界 | ✅13 |
| P10 评论通知黑盒 | `node docs/dev/test-p10-comments.mjs` | V5、评论/回复、限流、软删占位、通知、未读/已读和可见性边界 | ✅18 |
| P10 实时通知黑盒 | `node docs/dev/test-p10-realtime.mjs` | JWT 首帧、提交后通知、非法令牌关闭、Compose 升级配置 | ✅4 |
| P10 压力基线 | `node docs/dev/test-p10-load.mjs` | 10 并发/10 秒只读请求、吞吐、p50/p95/p99、错误率、压测后健康 | ✅ |
| P11 搜索黑盒 | `node docs/dev/test-p11-search.mjs` | 字段覆盖、受控 LIKE、可见性、排序、opaque cursor、边界码和 p95 | ✅13 |
| P11 索引分析 | `pwsh -File ops/rebuild-search-index.ps1` | ANALYZE、搜索索引和 EXPLAIN 查询计划 | ✅ |
| P12 视频地点黑盒 | `node docs/dev/test-p12-video-location.mjs` | 魔数/状态、ffprobe/ffmpeg 产物、MAP 坐标、EXIF 隐私、V7 和队列隔离 | ✅11 |
| P12-E1 分片黑盒 | `node docs/dev/test-p12-e1-upload.mjs` | V8 会话、真实预签名 PUT、恢复、合并/幂等、取消、过期和 MinIO 对象校验 | ✅11 |
| P12-E2 私有桶黑盒 | `node docs/dev/test-p12-e2-private.mjs` | key 语义残留 0、签名 URL 匿名可读、无签名直链/伪造签名/存在性探测全 403、Range 206 | ✅8 |
| P15 安全黑盒 | `node docs/dev/test-p15-security.mjs` | 安全响应头、注册（IP+XFF 末段归因）/社交/评论限流 429、注销全链路、通知保留配置；末段自清 rl:register:* 可重跑 | ✅13 |
| P16 编辑/列表黑盒 | `SCENARY_API_BASE_URL=http://localhost:8081/api/v1 node docs/dev/test-p16-gap.mjs` | 笔记编辑（媒体全量替换/越权/软删）、关注列表（分页/匿名视角/40400）、api-docs 生产关闭 | ✅20 |
| P18 治理黑盒 | `SCENARY_API_BASE_URL=http://localhost:8081/api/v1 node docs/dev/test-p18-governance.mjs` | 举报去重/阈值隐藏/可见性、评论举报、屏蔽双向过滤（feed/搜索/网格/详情）、恢复 | ✅19 |
| E3 逆地理黑盒 | `node docs/dev/test-p12-e3-places.mjs`（启用态另起内网 mock 容器，见证据 §3.3） | 关闭态空候选/匿名 401/参数 40000；启用态候选/缓存命中上游仅 1 次调用/超时 2s 降级/限流 429 | ✅8（关闭）· ✅11（启用） |
| E4 地图浏览器 | `SCENARY_FRONTEND_URL=http://localhost:8081 npx playwright test e2e/p12-e4-map.spec.js` | 选点回写坐标+触发逆地理、详情带坐标渲染小地图/无坐标零渲染、瓦片 404 降级坐标文本、390px | ✅12 |
| 组合 e2e 矩阵 | `npx playwright test`（7 spec × 3 浏览器） | 全部浏览器专项 + 既有 P8/P10/P11/P12/E3 回归 | ✅48×5 轮 |
| E5 地图黑盒 | `node docs/dev/test-e5-map.mjs` | 匿名公开面/私密无坐标排除/边界 40000/游标无重叠/屏蔽双向过滤 | ✅13 |
| 密码重置 | `pwsh -File ops/reset-user-password.ps1 -Username <u> -NewPassword <p>`（`-Preview`/`-Reactivate`） | jshell 生成 BCrypt → mysql 容器 UPDATE；旧密拒绝新密登录 | ✅ |
| 后端单元 | `cd backend && mvn test -q` | 认证/媒体/视频消费者/traceId/Feed/笔记(编辑/举报隐藏)/社交(列表/屏蔽)/评论/通知/搜索/分片上传/限流/注销/举报边界/逆地理(geohash/服务/解析)回归 | ✅94 |
| 前端单元 | `cd frontend && npm test` | request、媒体/视频、发布(编辑模式/逆地理联动/地图选点)、详情、登录、NoteCard、社交、评论/通知、搜索 API 与视图、注销危险区、图片编辑器、地图组件 | ✅58 |
| 前端浏览器 | `cd frontend && npm run test:e2e` | Chromium/Firefox/WebKit 的 390px 路由、评论/通知语义、键盘焦点、console/page error | ✅6 |
| P11 前端浏览器 | `cd frontend && SCENARY_FRONTEND_URL=http://localhost:8081 npx playwright test e2e/p11-search.spec.js` | Chromium/Firefox/WebKit 搜索 query/sort/highlight/可分享 URL | ✅3 |
| P12 前端浏览器 | `cd frontend && npx playwright test e2e/p12-video.spec.js` | Chromium/Firefox/WebKit 视频发布、单次提交、地点字段、详情播放 | ✅3 |
| 前端门禁 | `cd frontend && npm run build` | SFC 静态校验 | ✅107 modules |
| Phase 6 编排 | `docker compose config --quiet` | Compose 插值与 YAML | ✅ |
| Phase 6 全栈 | `docker compose up -d --build`、`docker compose ps` | backend health、服务依赖与 :8081 对外入口 | ✅ |
| Phase 6 冒烟 | 经 `http://localhost:8081/api/v1` 执行注册→上传→轮询→发布→匿名 feed | JWT 配置、MQ 缩略图、Nginx `/api` 与 `/minio`、SPA 路由 | ✅ |
| Phase 7 运维基线 | `pwsh -File ops/backup-mysql.ps1 -Preview`、`pwsh -File ops/watch-backend-errors.ps1 -Pattern ERROR`、`pwsh -File ops/check-dlq.ps1 -WarnAbove 0` | 备份/日志/DLQ 三件套（备份、DLQ 均经 Compose 容器执行） | ✅ |

脚本均已随机化用户名（v2.5 修复二次执行撞名）。IDEA HTTP 版冒烟：[smoke-backend.http](dev/smoke-backend.http)，图片夹具在 `docs/dev/fixtures/`（含真 webp）。

2026-09-03 P8 最终 Compose 复验由入口 `http://localhost:8081/api/v1` 得到认证/拦截链/上传/缩略图/端到端五组 `18/8/15/7/30`，共 `78/78`；P8 Compose 集成矩阵 `6/6`，合计 `84/84`。2026-09-04 P9 黑盒 `13/13`、后端 Docker Maven JUnit `26/26`、前端 Vitest `15/15`、Vite 构建 `101 modules`；P9 浏览器只读主流程确认首页卡片和详情页提供明确的点赞/收藏控件，`/bookmarks` 路由可达。2026-09-04 P10 初版黑盒 `18/18`、主机 Maven JUnit `32/32`、前端 Vitest `23/23`、Vite 构建 `105 modules`、三浏览器 Playwright `6/6`；补充黑盒 `4/4`，压力基线 10 并发/10 秒共 `15561` 请求、错误率 `0`、吞吐 `1555.01 req/s`、p50/p95/p99 `5.75/11.49/16.64ms`，V5 已应用，Compose 重建/健康和真实 WebSocket 升级链路均通过。2026-09-04 P11 已有 V5 数据升级至 V6、临时空 schema V1~V6 前向迁移通过；搜索黑盒 `13/13`，后端 JUnit `37/37`，前端 Vitest `27/27`，构建 `107 modules`，三浏览器专项 `3/3`，20 次查询 p95 `17.14ms`，索引分析/EXPLAIN 和 Compose 重建/健康均通过。P12 已有 V6 数据升级至 V7，视频地点黑盒 `11/11`，后端 JUnit `39/39`，前端 Vitest `31/31`，构建 `107 modules`，三浏览器专项 `3/3`；容器内 ffmpeg/ffprobe `8.1.2`，MAP/EXIF 隐私和视频队列隔离均通过。P12-E1 已有 V7 数据升级至 V8，分片黑盒 `11/11`，后端 JUnit `50/50`，前端 Vitest `33/33`，构建 `107 modules`，三浏览器专项 `3/3`；真实 MinIO 预签名 PUT、断点恢复、合并幂等、取消/过期清理和 Compose 健康均通过。

P8 完整证据、环境指纹与验证边界归档在 [2026-09-03-P8改进验收](evidence/2026-09-03-P8改进验收.md)；P9 证据归档在 [2026-09-04-P9社交最小闭环验收](evidence/2026-09-04-P9社交最小闭环验收.md)；P10 初版证据归档在 [2026-09-04-P10评论与通知验收](evidence/2026-09-04-P10评论与通知验收.md)，补充证据归档在 [2026-09-04-P10未覆盖项补充验收](evidence/2026-09-04-P10未覆盖项补充验收.md)；P11 证据归档在 [2026-09-04-P11搜索与发现验收](evidence/2026-09-04-P11搜索与发现验收.md)；P12 基础证据归档在 [2026-09-04-P12视频与地点验收](evidence/2026-09-04-P12视频与地点验收.md)，E1 证据归档在 [2026-09-04-P12-E1分片上传验收](evidence/2026-09-04-P12-E1分片上传验收.md)；历史 MVP 基线见 [2026-09-02-MVP复验报告](evidence/2026-09-02-MVP复验.md)。后续 Phase/功能环节仍必须先按 [证据链规范](evidence/README.md) 落盘报告；P12 当前范围已完成。

## 4. 待办清单（按优先级）

> 当前工作边界：Phase 7、P8~P12（含 E1~E5 全部增强包）、P15、P16~P18 均已通过全量出口验收（P15 仅邮件找回 03b 延后）；已立项队列清空，候选评估见 07。

1. **已立项队列已清空（E3/E4/E5 全部完成，2026-09-28）**：后续候选（N2 草稿箱/N3 评论点赞/N1 行为数据+B5 标签/N4 缩略图多尺寸+B6 等）均为提案未立项，评估见 [07 缺口评估](07-功能缺口评估与后续候选.md)；转正走 [06 §8](06-功能拓展与候选立项规划.md)。E3 逆地理真实启用需 `docker compose --profile geo up -d nominatim` + `.env` 配 `NOMINATIM_PBF_URL`（区域 OSM pbf）与 `SCENARY_PLACE_PROVIDER_ENABLED=true`（证据见 evidence/2026-09-19-P12-E3 报告）。
2. **CI 已转绿（2026-09-30，run 36666790653）**：真栈流水线全通过（mvn test / vitest / build / Compose+chromium e2e / 黑盒 4 套件 / 密钥扫描）。根因链与修复沉淀于 learning 27；失败现场诊断走 artifact（stack-diagnostics，含 compose-up/backend 日志与 ps）；排障期临时权限与诊断脚手架已全部回收。
3. **P15 遗留小项**：P15-03b 邮件找回（待外部 SMTP 凭据，03 附 10 未勾）；CSP 从 Report-Only 转 enforce（先盘点 index.html 两处内联脚本）；HSTS 随 TLS 部署形态启用。
4. **回滚提示**：E2 私有桶出问题时 `pwsh -File ops/set-bucket-policy.ps1 -Policy download` + `.env` 设 `SCENARY_MEDIA_PRESIGN_READ=false` 重启 backend 即回直链模式（v2.48.2 已按 aws-cli 工具链修复并双向实证）；**E3 回滚**：`.env` 摘除 `SCENARY_PLACE_PROVIDER_ENABLED` 重启 backend 即回空候选（已验证 8/8）；**E4 回滚**：`VITE_ENABLE_MAP=false` 重新构建 frontend 即整体摘除（已验证构建产物零 leaflet）。
5. **历史媒体 URL 迁移**：已由 E2-03 完成 URL→key 回填（`ops/migrate-media-urls.ps1 -ToKeys`）；换域名部署时仍用默认模式按新旧 public host 重建。
6. **长期 TODO（暂不排期）**：P13 内容审核、P14 规模化运维；两者的设计草案和重新立项出口条件保留在 [05 后续开发路线图](05-后续开发路线图与实施手册.md) 中。
7. **下一批候选（提案，未立项）**：夹缝包（草稿箱/评论点赞）、行为数据+标签（B5 合并）、多尺寸缩略图+OG/SEO（B6 合并）——缺口评估与建议档位见 [07](07-功能缺口评估与后续候选.md) §3~§4；转正流程走 [06 §8](06-功能拓展与候选立项规划.md)。

> 当前施工记录：P8-01~P8-22、P9-01~P9-05、P10-01~P10-06、P10-S01~P10-S05、P11-01~P11-05、P12-01~P12-05、P12-E1-01~P12-E1-04、P15-01~P15-06（03b 延后）与 P12-E2-01~E2-03 均已落地并完成验证；E2 出口报告为 `docs/evidence/2026-09-12-P12-E2私有桶与短时签名验收.md`，最新学习笔记为 `docs/learning/22-P12-E2私有桶key语义与签名收口.md`。

## 5. 关键决策与坑位速查（细节见对应文档）

| 主题 | 出处 |
|---|---|
| Boot 基线为何是 3.5.16 且不依赖 start.spring.io | CHANGELOG v1.3/v1.4 |
| 后端运行需 run-dev.sh 注入 env + JDK 守卫原因 | learning/01、CHANGELOG v1.7 |
| healthcheck 内插密码的明文风险 | learning/03 |
| MQ 计数器恒 0 怪癖 + 发布早于绑定黑洞 | learning/07 |
| feed 两级缓存（版本化 L1 / `note:card:{id}`）与默认分页边界 | docs/01 §5.3、CHANGELOG v2.16 |
| visibility 契约补丁（POST /notes 可选字段） | CHANGELOG v2.3、docs/02 §5.1 |
| 前端双主题实现（token 化 + html.dark + FOUC 预判脚本） | learning/09、assets/main.css |
| Phase 6 容器化文件与 Docker 验收边界 | docs/03 §6、CHANGELOG v2.9 |
| 后端健康检查依赖 `wget` | backend/Dockerfile、docker-compose.yml |
| production profile 配置与 `SCENARY_JWT_*` / `MYBATIS_*` 映射 | learning/11、CHANGELOG v2.11/v2.12 |
| `/minio` 必须用 `^~` 优先于静态图片正则 | frontend/nginx.conf、CHANGELOG v2.13 |
| MinIO 官方 Hub 镜像已下架（bitnamilegacy 日期 tag + aws-cli 建桶/策略切换）；compose env"看似数字"的值必须加引号 | learning/27、CHANGELOG v2.48/v2.48.2 |

## 6. 配置与安全速记

服务账号、密码和 JWT 密钥仅存于本机 `.env`，不写入交接文档、日志或提交记录。全栈编排只暴露 `:8081`；中间件管理端口仅由开发期 `docker-compose.middleware.yml` 映射。
