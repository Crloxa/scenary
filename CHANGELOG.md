# Changelog · Scenary

## [v2.46] · 2026-09-19 · P12-E3 逆地理编码完成（契约 v1.9）

- **契约 v1.9**：02 新增 §7B 地点模块——`GET /places/reverse-geocode?latitude=&longitude=`（仅登录用户），响应 `{placeName, provider, cached}`；§1.4 限流表新增 30 次/分钟/用户行；错误面 40100/40000/42001，provider 侧任何失败不产生 5xx（影响 `docs/02-API接口规范.md`）。
- **后端 place 包**：`PlaceProvider` SPI（实现必须自托管、失败空候选）+ `NominatimProvider`（Spring 内置 RestClient 零新 Java 依赖，连接/读超时同用 2s，jsonv2 解析优先 name 退 display_name 截断 128 列宽）+ `PlaceService`（限流→校验→geohash-5 缓存 `place:rg:*` TTL 30 天→provider；provider 抛错折叠空候选）+ `PlaceController`；`RateLimitService.places()` 键 `rl:place:{userId}`；WebConfig 登记 `/api/v1/places/reverse-geocode` 防匿名可达（P18 学习 24 教训）；无新迁移（影响 `backend/src/main/java/com/scenary/place/`、`common/RateLimitService.java`、`config/WebConfig.java`）。
- **设计修正**：只缓存成功地名——超时/失败的空结果若落缓存会把整个 geohash 网格污染 30 天；SPI 约定"实现不抛错"之上 Service 仍兜底 try/catch（影响 `place/PlaceService.java`）。
- **容器编排**：docker-compose 新增 `nominatim` 服务（mediagis/nominatim:4.5，挂 `geo` profile 默认不启动，无 ports 映射坐标不出内网，独立数据卷 `scenary-nominatim-data`）；backend environment 新增 `SCENARY_PLACE_*`（ENABLED 默认 false / URL / TIMEOUT_MS 默认 2000 / CACHE_TTL_DAYS 默认 30）与 `SCENARY_RATELIMIT_PLACE_PER_MIN` 默认 30（影响 `docker-compose.yml`、`application.yml`）。
- **前端联动（E3-03）**：`src/api/place.js`；PublishView 坐标齐备后防抖 600ms 查候选——placeName 为空自动回填（仍可编辑）、已填不静默覆盖改为候选条"使用候选"按钮确认、空候选/失败静默降级不阻塞发布；卸载清理定时器与在途请求（影响 `frontend/src/views/PublishView.vue`、`frontend/src/api/place.js`）。
- **测试与证据**：JUnit 新增 17 例（Geohash 权威向量 ezs42/u4pru、PlaceService 7 例、NominatimProvider 解析 7 例）全量 94/94；Vitest 新增 PublishPlaceFlow 4 例全量 49/49、构建 112 modules；黑盒 `test-p12-e3-places.mjs` 两模式（关闭态 8/8；启用态 11/11——缓存命中上游仅调 1 次、超时 elapsed=2031ms 降级、限流 429）+ 内网 mock provider `mock-nominatim.mjs`；隐私断言 mock 容器无发布端口；Playwright 新增 `p12-e3-place.spec.js` 三浏览器 12/12、既有 4 spec 12/12 无回归；证据 [evidence/2026-09-19-P12-E3逆地理编码验收](docs/evidence/2026-09-19-P12-E3逆地理编码验收.md)（影响 `docs/dev/`、`frontend/e2e/`、`frontend/tests/`）。
- **测试夹具修正**：p12-video.spec 补 geocode 路由 mock（空候选）——发布页新增该调用后，不 mock 会以假令牌打到真实后端 401 触发全局刷新/登出流程卡死发布页（影响 `frontend/e2e/p12-video.spec.js`）。
- **文档同步**：03 附 8 全勾；01 §3.1 两行更新（provider/Leaflet 登记状态）；05 §11 执行状态；HANDOVER 现状/矩阵/待办；docs/README 时间线；学习沉淀 `learning/25-WebKit路由拦截与E2E时序.md`（WebKit 多路由 LIFO+expect 轮询偶发 XHR 无响应的排查与根治）。

## [v2.45] · 2026-09-19 · 文档结构重整（文档地图 / 缺口评估 / 历史归档）

- **文档地图**：新增 [docs/README.md](docs/README.md) 导航中枢——四层渐进披露（根入口→导航→正文→证据/学习/归档）、全部文档清单（含生命周期标注）、7 个场景阅读路径、里程碑×Checklist×证据×学习沉淀的时间线索引表、目录树注释；文档集合变化或新里程碑验收落盘时更新（影响 `docs/README.md`）。
- **缺口评估**：新增 [docs/07-功能缺口评估与后续候选.md](docs/07-功能缺口评估与后续候选.md)——沿"地图→时间线→Checklist→契约→证据→代码核查"方法盘点：功能全景（已验收 7 能力域 / 在建队列 E3/E4/E5 与 P15 尾项 / 06 池候选 / 明确不做）与新识别缺口 5+1 项（N1 行为数据埋点、N2 草稿箱、N3 评论点赞、N4 多尺寸缩略图 srcset、N5 robots/sitemap 并入 B6、随 E4 评估坐标模糊化，均附 2026-09-19 代码核查证据），并给出排在 E3/E4/E5 之后的提案序列；全部为提案未立项，转正走 06 §8（影响 `docs/07-功能缺口评估与后续候选.md`）。
- **历史归档**：`git mv` 三份已完成使命的文档至 `docs/archive/`（04-产品与工程改进总纲、project-audit-2026-09-11、frontend-audit-2026-09-02），内容零改动（仅对归档件内相对链接机械加 `../` 前缀适配新层级）；原路径留跳转桩，保证 evidence/learning/CHANGELOG 历史条目中的相对链接不断链（影响 `docs/archive/` 三份正文、`docs/04-产品与工程改进总纲.md` 等三份桩文件）。
- **入口同步**：AGENTS.md 阅读顺序插入 docs/README（顺序 2）与规划场景阅读路径、§5 快照更新至 v2.45；README 文档索引重排（新增 docs/README、07、archive、dev 行，14 行证据清单收敛为 evidence 目录行）；HANDOVER 头部/§1 现状/§4 待办同步（影响 `AGENTS.md`、`README.md`、`docs/HANDOVER.md`）。
- **链接修正**：03（2 处 04 引用、1 处审计引用）、05（3 处审计引用）、06（1 处审计引用）指向归档新路径；06 状态行补 07 指针（影响 `docs/03-MVP实施与Docker部署.md`、`docs/05-后续开发路线图与实施手册.md`、`docs/06-功能拓展与候选立项规划.md`）。
- **边界**：`docs/evidence/` 与 `docs/learning/` 内容零改动（证据链与学习沉淀不受结构重整影响）；`docs/dev/`、`ops/`、业务代码与契约（02 仍 v1.8）零改动；本条目为纯文档结构变更，不改变任何验收状态，下一步仍是 P12-E3。

## [v2.44] · 2026-09-19 · P16 夹缝任务包 / P17 修图与滤镜 / P18 社区治理完成

- **P16-01 笔记编辑**：`PUT /notes/{id}`（02 §5.11）——owner/软删校验、媒体全量替换（先整体解绑再重绑避开 `uk_media_note_order` 冲突，`order_no` NOT NULL 以 0 作游离标记）、cover/media_count 重算、复用发笔记限流、卡片与 feed 版本缓存失效；前端 `/publish/:noteId` 编辑模式与详情页编辑入口（影响 `note/`、`media/MediaMapper`、`frontend/src/views/PublishView.vue` 等）。
- **P16-02 关注列表**：`GET /users/{id}/followers` 与 `/following`（02 §3.9/§3.10）——follows.id 倒序游标、status=1 过滤、登录视角 following、头像签名出口；前端 `FollowListView` 与个人页入口（影响 `social/`、`frontend/src/views/FollowListView.vue` 等）。
- **P16-03 OpenAPI**：springdoc 2.8.9（01 §3.1 登记），api-docs 收敛 `/api/v1/api-docs`，nginx 反代 swagger-ui/webjars，`SCENARY_APIDOC_ENABLED` 生产默认关（影响 `backend/pom.xml`、`config/OpenApiConfig.java`、`frontend/nginx.conf`）。
- **P17 修图与滤镜**：`utils/imageEditor.js` 纯函数 + `ImageEditorModal.vue` canvas 弹窗（5 比例裁剪/90° 旋转/16 滤镜/JPEG 导出长边 4096），发布页 ✎ 入口产物替换重传；`VITE_ENABLE_EDITOR=false` 构建产物编辑器 chunk=0 实证（影响 `frontend/src/utils/`、`frontend/src/components/`、PublishView）。
- **P18 举报与屏蔽**：V10（reports/user_blocks/notes.report_count）+ V11（visibility CHECK 放开至 0~3）；`POST /reports` 唯一去重、20/10min 限流、阈值 5 自动隐藏 visibility 1→3（仅作者可见，feed 版本失效）；`PUT/DELETE /users/{id}/block` 双向过滤 feed/搜索/网格/详情；前端举报弹层与屏蔽入口；`ops/list-reports.ps1` 只读报表；契约 02 v1.8（影响 `report/`、`note/`、`social/`、`search/`、`WebConfig`、`docs/02`）。
- **验收闭环**：P16 黑盒 `20/20`、P18 黑盒 `19/19`；15 个黑盒套件 190 断言全绿；后端 JUnit `81/81`、前端 Vitest `45/45`、构建通过；证据 `docs/evidence/2026-09-19-P16夹缝任务包验收.md`、`2026-09-19-P17修图与滤镜验收.md`、`2026-09-19-P18社区治理验收.md`；学习沉淀 23（媒体重绑与唯一键）、24（CHECK 约束与拦截器白名单）；03 附 11~13 全部勾选并同步 05 §11、HANDOVER、README、AGENTS。
- **边界**：P17 canvas 真机剧本、举报/屏蔽三浏览器 UI 剧本列边界；搜索屏蔽过滤依赖可选 Authorization 头（匿名不过滤）；评论列表不叠加屏蔽过滤；E3/E4/E5 与 P15-03b 仍为队列；P13/P14 维持长期 TODO。

## [v2.43] · 2026-09-19 · 06 规划转正立项 P16/P17/P18/E5（契约 v1.7）

- **未提交工作落库**：P15 账号与安全基线（19950c4）、P12-E2 私有桶与短时签名（d7fc27b）经全量复验（JUnit 68/68、Vitest 35/35、构建、P15 黑盒 13/13、E2 黑盒 8/8、Compose 六容器健康）后分批提交；夹具假令牌误报修复（9194a6d）、docs/01 依赖登记补登（9e0ab40）。
- **立项**：[06 功能拓展与候选立项规划](docs/06-功能拓展与候选立项规划.md) 经产品确认转正——P16 夹缝任务包（笔记编辑/关注列表/OpenAPI，05 §12）、P17 修图与滤镜（05 §13）、P18 社区治理最小闭环（举报+屏蔽，05 §14）、E5 地图浏览占位（05 §15，依赖 E4）；编号映射：06 原文 P16/P17 顺延为 P17/P18（影响 `docs/05-后续开发路线图与实施手册.md` §1/§10/§11/§12~§15、`docs/03-MVP实施与Docker部署.md` 附 11~13）。
- **契约先行**：02 升 v1.7——新增 §5.11 PUT /notes/{id} 编辑笔记（媒体全量替换语义，复用发笔记限流场景）、§3.9/§3.10 GET /users/{userId}/followers 与 /following（影响 `docs/02-API接口规范.md`）。
- **依赖登记**：01 §3.1 springdoc 行更新为"随 P16-03 安装"（springdoc-openapi-starter-webmvc-ui 2.8.x，生产默认关）（影响 `docs/01-技术栈与总体架构.md`）。
- **边界**：本轮仅立项与契约文档，未动业务代码；P12-E3/E4 与 P15-03b 仍在队列；05 §6.5 E3 契约编号顺延为 v1.8。

## [v2.42] · 2026-09-19 · 功能拓展与候选立项规划落盘（提案，未立项）

- **差距盘点**：新增 [docs/06-功能拓展与候选立项规划.md](docs/06-功能拓展与候选立项规划.md)，以"愿景落差 / 产品补强 / 工程模块化"三个透镜盘点除线上部署外的候选功能：A 类 4 项（修图与滤镜、人物检测安全门分段、生物物种树、全局地图浏览页）、B 类 8 项（笔记编辑、关注/粉丝列表、举报、屏蔽、标签、OG 分享卡、邮件通知、数据导出）、C 类 5 项（媒体管线 SPI、领域事件+Outbox、独立 worker、可观测性最小集、OpenAPI）。
- **优先级建议**：① 夹缝任务（笔记编辑/关注列表/OpenAPI）→ ② P16 修图与滤镜 → ③ P17 社区治理最小闭环（举报+屏蔽）→ ④ E5 地图浏览（依赖 E4）→ ⑤ 安全门第二段/物种树按愿景节奏重新立项；含各档进入条件、出口证据与规模估算。
- **现状核查依据**：grep 当前工作区全部 Controller 端点与前端视图，确认笔记编辑、关注列表、举报/屏蔽、标签、图片编辑均不存在；思绪.txt 逐条对照表记录 F7（修图）为从未进入任何一期规划的愿景条目（影响 `docs/06-功能拓展与候选立项规划.md`）。
- **边界**：仅新增提案文档并登记索引，未立项任何阶段、未改代码/契约/迁移；P12-E3/E4 与 P15-03b 仍是当前施工队列；候选条目转正须按 06 §8 流程走。

## [v2.41] · 2026-09-12 · P12-E2 私有桶与短时签名完成（修复私密媒体匿名可达漏洞）

- **E2-01 签名读能力**：`MinioService.presignGet` 复用 E1 同源反代机制；`viewUrl` 容错读（key/遗留 URL→签名 URL）；`SCENARY_MEDIA_PRESIGN_READ=false` 为回滚直链模式；新增 `ops/set-bucket-policy.ps1`（none/download 双向），`MINIO_BUCKET_POLICY` 默认 none=私有，minio-init 实测 `set to private`（影响 `backend/.../media/MinioService.java`、`config/MinioProperties.java`、`frontend/nginx.conf` 未动反代、`docker-compose.yml`、`ops/set-bucket-policy.ps1`）。
- **E2-02 读路径切 key**：图片/视频上传、缩略图消费者、视频转码、头像的 URL 列改存 key（`updateProcessResult`/`updateVideoProcessResult` 去 URL 入参）；读路径签名出口覆盖媒体 toItem、笔记详情/网格/发布响应、feed 两级缓存（`FeedService.sign`，缓存存 key 组装时签名）、搜索、收藏、评论/通知作者头像；跨模块经 `NoteService.viewUrl`/`UserService.viewUrl` 窄门面；施工中修复发布响应 `NoteCreatedVO.coverUrl` 漏签名（前端 e2e ERR_INVALID_URL 抓出）（影响 `media/`、`mq/ThumbnailConsumer`、`note/`、`feed/`、`search/`、`social/`、`comment/`、`notification/`、`user/`）。
- **E2-03 历史回填**：`ops/migrate-media-urls.ps1 -ToKeys`，Preview 命中 media 365/218、notes 218、users 26 行，执行后黑盒断言 `http%` 残留=0（影响 `ops/migrate-media-urls.ps1`）。
- **契约**：02 新增 §1.5"媒体 URL 生命周期"（非破坏性：字段结构不变，值为短时签名 URL，TTL 300s 可配）；5 个黑盒脚本断言升级为"对象路径+签名"语义（影响 `docs/02-API接口规范.md`、`docs/dev/test-{media36,thumbnail37,e2e38,p12-video-location,p12-e1-upload}.mjs`）。
- **验收闭环**：E2 黑盒 `8/8`（无签名直链 403=漏洞封死、伪造签名 403、存在性探测 403、签名可读 200、Range 206、key 语义残留 0）；后端 JUnit `68/68`、前端 Vitest `35/35`、构建通过；13 个既有黑盒套件 `147/147`；三浏览器 Playwright `6/6`（`--workers=1`，并发时 WebKit 与 ffmpeg 资源竞争属环境偶发）；压测 `17739` 请求错误率 0、p50/p95/p99 `5.22/9.48/17.45ms`（与 P10 基线同量级）；证据/学习/Checklist 同步（影响 `docs/dev/test-p12-e2-private.mjs`、`docs/evidence/2026-09-12-P12-E2私有桶与短时签名验收.md`、`docs/learning/22-P12-E2私有桶key语义与签名收口.md`、03 附 7、05 §11）。
- **边界**：签名 TTL 内"URL+签名即授权"，页面长停留需刷新重新获取；缩略图/转码产物同样签名，CDN 缓存仍属 P14；回滚路径 = `set-bucket-policy.ps1 -Policy download` + `PRESIGN_READ=false`（已验证命令）。

## [v2.40] · 2026-09-12 · P12 全量自检与 P15 账号安全基线完成

- **P12 自检确认**：按 HANDOVER 矩阵全量复跑 P12/P12-E1 黑盒 `11/11+11/11`、后端 JUnit `50/50`、前端 Vitest `33/33`、构建、三浏览器 `3/3` 全绿后才开始 P15（影响 `docs/HANDOVER.md` 测试矩阵口径不变）。
- **P15-01 限流基线**：新增 `common.RateLimitService`（Redis INCR 模式，42001 契约不变），接入注册/发笔记/社交/上传；修正注册限流 IP 归因——nginx 传 `$proxy_add_x_forwarded_for`、后端取 XFF 末段、回环豁免可配；审计文档勘误"注册无限流"结论（实为归因缺陷），追勘误节 §7（影响 `backend/.../common/RateLimitService.java`、`auth/AuthController.java`、`frontend/nginx.conf`、`docker-compose.yml`、`.env.example`、`docs/project-audit-2026-09-11.md`）。
- **P15-02 安全响应头**：nginx 全局增加 X-Frame-Options/X-Content-Type-Options/Referrer-Policy 与 CSP Report-Only；WebKit console 断言抓出缺 `report-to` 指令缺陷后补齐（`/csp-report` 丢弃收集）；HSTS 留待 TLS 部署形态（影响 `frontend/nginx.conf`）。
- **P15-03a 密码找回**：新增 `ops/reset-user-password.ps1`（jshell + spring-security-crypto 生成 BCrypt，-Preview/-Reactivate，自定义 Maven 仓库解析）；端到端实测旧密 40000/新密 code=0。03b（email+SMTP）延后，01 §3.1 保留评估行（影响 `ops/reset-user-password.ps1`）。
- **P15-04 账号注销**：契约 v1.6 新增 `DELETE /users/me`；`AccountDeactivationService` 同事务匿名化+笔记评论软删+头像对象删除，事务提交后吊销令牌；首次实现引入 `UserService→CommentService` 形成 bean 环致启动失败，拆单向编排服务解耦（学习笔记 21）；前端个人中心新增注销危险区（影响 `backend/.../user/`、`frontend/src/views/ProfileView.vue`、`docs/02-API接口规范.md`）。
- **P15-05/06**：通知保留策略 @Scheduled 分批清理（已读超 90 天，env 可配）；审计探针正规化为 `docs/dev/test-p15-security.mjs`；根 `/test-results/` 入 .gitignore 生效（影响 `notification/`、`docs/dev/test-p15-security.mjs`、`.gitignore`）。
- **验收闭环**：后端 JUnit `68/68`、前端 Vitest `35/35`、构建 `107 modules`、12 个既有黑盒套件 `147/147` 无回归、P15 黑盒 `13/13`、三浏览器 Playwright `6/6`、Compose 重建健康；证据/学习/Checklist/契约同步（影响 `docs/evidence/2026-09-12-P15账号与安全基线验收.md`、`docs/learning/21-P15限流归因与账号注销的bean环.md`、03 附 10、05 §7/§11、02 v1.6）。
- **边界**：P15-03b 邮件找回延后（待外部 SMTP 凭据）；CSP 处于 Report-Only 观察期，enforce 化另行小任务；P12-E2/E3/E4 未开工为当前施工队列；P13/P14 维持长期 TODO。

## [v2.39] · 2026-09-11 · 全栈不足盘点审计与 P12-E2/E3/E4、P15 立项

- **实测审计落盘**：Docker 全栈 8 项探针 + 6 项代码核查，确认私密笔记封面匿名可达（隐私漏洞，noteId=188 复现）、桶级匿名存在性探测、注册与社交写接口无限流、密码找回/账号注销缺失（users 无 email 字段）、安全响应头五项全缺、通知无清理策略（影响 `docs/project-audit-2026-09-11.md`）。
- **既定增强计划补齐**：私有桶短时签名、逆地理编码、地图 UI 此前仅有一句话边界描述，本轮补齐为任务级计划——P12-E2（05 §6.4）、P12-E3（05 §6.5）、P12-E4（05 §6.6）；实测新发现缺口合并立项 P15 账号与安全基线（05 §7）；05 章节重编号（P13/P14 移至 §8/§9，施工顺序 §10、执行状态 §11），路线图总览与推荐顺序同步（影响 `docs/05-后续开发路线图与实施手册.md`）。
- **Checklist 登记**：03 新增附 7~10 四份未勾选 Checklist（E2/E3/E4/P15），作为后续施工唯一进度真相源（影响 `docs/03-MVP实施与Docker部署.md`）。
- **依赖评估登记**：01 §3.1 更新"反向地理编码供应商"行并新增"前端地图库（Leaflet）+ 瓦片源"、"spring-boot-starter-mail"两行，均标注"开工前不安装"（影响 `docs/01-技术栈与总体架构.md`）。
- **仓库卫生**：根目录 `/test-results/` 补入 `.gitignore`（影响 `.gitignore`）。
- **边界**：本轮仅文档与 gitignore 变更，未改业务代码、API 契约与迁移；四阶段均未开工、无验收状态变化；审计探针在开发库残留 `probe*` 前缀用户 7 个与私密探针笔记 1 条（审计文档 §6 已记录）。

## [v2.38] · 2026-09-04 · P12-E1 分片上传验收闭环

- **功能落地**：新增 V8 视频上传会话/分片表、8MiB 预签名 PUT、MinIO 服务端 compose/copy、服务端分片实际大小/总大小/魔数校验、owner 隔离、幂等 complete、取消和过期清理；图片代理上传与 P12 视频转码链路保持不变（影响 `backend/src/main/java/com/scenary/media/`、`backend/src/main/resources/db/migration/V8__video_upload_sessions.sql`、`backend/src/main/resources/mapper/VideoUploadSessionMapper.xml`、`docker-compose.yml`）。
- **前端体验**：视频上传改为会话元数据+已完成片号恢复，补充进度展示、同文件刷新恢复、上传中离开保护、失败重试和同一次 complete；Nginx 增加预签名同源反代配置（影响 `frontend/src/api/media.js`、`frontend/src/views/PublishView.vue`、`frontend/e2e/p12-video.spec.js`、`frontend/nginx.conf`）。
- **验收闭环**：P12-E1 黑盒 `11/11`、后端 JUnit `50/50`、前端 Vitest `33/33`、构建 `107 modules`、三浏览器 Playwright `3/3`、Compose 重建/健康和 V8 实查通过；证据与学习笔记已归档并同步 Checklist、HANDOVER、README、AGENTS（影响 `docs/evidence/2026-09-04-P12-E1分片上传验收.md`、`docs/learning/20-P12-E1预签名与断点续传.md`）。
- **边界**：私有桶/短时播放签名、外部逆地理编码和地图 UI 仍属于后续增强；本轮不执行 schema 回滚、`docker compose down -v` 或未核对的历史 DLQ/业务数据清理。

## [v2.36] · 2026-09-04 · P12 视频与地点基础链路完成

- **功能落地**：完成 V7 视频媒体字段与笔记坐标、MP4/MOV/WEBM 魔数校验、200MB/120秒/3840长边限制、独立 `video.transcode` 队列与 `video.dlq`，容器内 ffprobe/ffmpeg 生成封面和 480p/720p MP4；原始视频只保留 object key，不进入响应（影响 `backend/src/main/java/com/scenary/media/`、`backend/src/main/java/com/scenary/mq/VideoTranscodeConsumer.java`、`backend/src/main/resources/db/migration/V7__video_location.sql`、`backend/Dockerfile`、`docker-compose.yml`）。
- **地点与前端**：完成服务端只读 EXIF GPS 候选、MAP/手工坐标发布和公开响应隐私保护；发布页支持视频预览/处理中/失败重传/离开保护，详情页使用转码视频播放（影响 `backend/src/main/java/com/scenary/note/`、`frontend/src/views/PublishView.vue`、`frontend/src/views/NoteDetailView.vue`、`frontend/src/api/`）。
- **缺陷修复**：发现 16:9 视频按 854×480 等比缩放会得到奇数宽度 853，导致 libx264 失败；转码滤镜增加偶数尺寸 pad，实测最终产物为 854×480（影响 `backend/src/main/java/com/scenary/media/VideoTranscodeService.java`）。
- **验收闭环**：P12 黑盒 `11/11`、后端 JUnit `39/39`、前端 Vitest `31/31`、构建 `107 modules`、三浏览器 Playwright `3/3`、Compose 重建/健康和 V7 实查通过；证据与学习笔记已归档并同步 Checklist、HANDOVER、README、AGENTS（影响 `docs/evidence/2026-09-04-P12视频与地点验收.md`、`docs/learning/19-P12视频转码与地点隐私.md`、`frontend/e2e/p12-video.spec.js`）。
- **边界**：分片/预签名/断点续传、私有桶/短时签名、外部逆地理编码和地图 UI 延后到 P12-E1/后续增强；现有 Compose MinIO public-read 边界保持不变。

## [v2.35] · 2026-09-04 · P12 视频与地点基础链路立项

- **范围先行**：P12 首期收敛为单文件视频基础上传/转码、封面与 480p/720p MP4、手工/地图坐标及 EXIF 隐私读取；分片/预签名断点续传、外部逆地理编码和地图 UI 延后，不在本轮扩大范围（影响 `docs/05-后续开发路线图与实施手册.md`、`docs/03-MVP实施与Docker部署.md`）。
- **依赖冻结登记**：允许引入容器运行时 `ffmpeg/ffprobe` 和 Java `metadata-extractor`，前者承担容器探测/转码，后者只读图片 EXIF GPS；均登记回装位置、限制与回滚边界，不引入地图供应商或前端地图依赖（影响 `docs/01-技术栈与总体架构.md`）。
- **契约登记**：API 升级为 v1.4，新增 `/media/videos`、视频状态/播放字段和笔记坐标字段；实现与 P12 验收待本阶段后续提交补齐（影响 `docs/02-API接口规范.md`）。

## [v2.34] · 2026-09-04 · P11 搜索与发现完成

- **功能落地**：新增公开笔记搜索后端与 `GET /api/v1/search/notes`，覆盖标题/正文/地点/作者昵称，采用受控 LIKE、公开/软删/禁用作者过滤、`recent`/`relevance` 排序、opaque cursor 和纯文本 highlight（影响 `backend/src/main/java/com/scenary/search/`、`backend/src/main/resources/mapper/SearchMapper.xml`、`docs/02-API接口规范.md`）。
- **数据与前端**：应用 Flyway V6 搜索读路径索引；新增导航搜索入口、`/search` 路由、结果加载更多/空态/失败重试和可分享 URL；不引入 Elasticsearch、标签字段、推荐算法或搜索缓存（影响 `backend/src/main/resources/db/migration/V6__search_indexes.sql`、`frontend/src/api/search.js`、`frontend/src/views/SearchView.vue`、`frontend/src/components/TopNav.vue`、`frontend/src/components/NoteCard.vue`）。
- **验收闭环**：空 schema Flyway V1~V6 前向迁移、已有 V5 数据升级、黑盒 `13/13`、搜索 p95 `17.14ms`、索引 `ANALYZE/EXPLAIN`、后端 JUnit `37/37`、前端 Vitest `27/27`、构建 `107 modules`、Chromium/Firefox/WebKit `3/3` 均通过；证据与学习笔记已归档并同步 Checklist、HANDOVER、README、AGENTS（影响 `docs/evidence/2026-09-04-P11搜索与发现验收.md`、`docs/learning/18-P11搜索与发现.md`）。

## [v2.33] · 2026-09-04 · P11 搜索与发现立项

- **契约先行**：将 P11 首期收敛为公开笔记搜索，覆盖标题/正文/地点/作者昵称，支持 `recent`/`relevance`、opaque cursor 和纯文本 `highlight`；明确 q/sort/cursor 边界以及不实现标签、推荐和 Elasticsearch（影响 `docs/02-API接口规范.md`、`docs/05-后续开发路线图与实施手册.md`）。
- **数据与依赖决策**：登记 Flyway V6 搜索读路径索引与 `ANALYZE TABLE`/`EXPLAIN` 重建入口；采用受控 LIKE，不新增第三方依赖，不复用 Feed 搜索缓存（影响 `docs/01-技术栈与总体架构.md`、`docs/03-MVP实施与Docker部署.md`）。
- **进度登记**：新增 P11 Checklist，P8/P9/P10 完成状态保持不变；实现与验收待本阶段后续提交补齐（影响 `docs/03-MVP实施与Docker部署.md`）。

## [v2.32] · 2026-09-04 · P10 未覆盖项补充验收闭环

- **验收完成**：补充敏感词黑盒 `18/18`、真实 WebSocket 黑盒 `4/4`、Chromium/Firefox/WebKit Playwright `6/6`、键盘/读屏语义专项和正式压力基线（10 并发/10 秒、`15561` 请求、错误率 `0`、p50/p95/p99 `5.75/11.49/16.64ms`）；Compose 重建、服务健康与 WebSocket 升级链路通过（影响 `docs/evidence/2026-09-04-P10未覆盖项补充验收.md`、`docs/dev/test-p10-realtime.mjs`、`docs/dev/test-p10-load.mjs`）。
- **状态闭环**：勾选 P10-S01~S05 与补充出口，同步 HANDOVER、README、AGENTS、路线图和学习索引；保留 P10 v2.29 原始证据不回改（影响 `docs/03-MVP实施与Docker部署.md`、`docs/HANDOVER.md`、`README.md`、`AGENTS.md`、`docs/05-后续开发路线图与实施手册.md`、`docs/learning/17-P10补充验收与实时通道.md`）。

## [v2.31] · 2026-09-04 · P13/P14 延后至长期 TODO

- **路线调整**：将 P13 内容审核、P14 规模化运维从当前施工顺序移入长期 TODO；近期路线收敛为 P11 搜索与发现、P12 视频与地点，保留 P13/P14 的设计草案和重新立项出口条件（影响 `docs/01-技术栈与总体架构.md`、`docs/05-后续开发路线图与实施手册.md`）。
- **状态同步**：同步 README、AGENTS 和 HANDOVER 的当前路线、版本与待办说明；未修改 API 契约、实现代码和历史变更记录（影响 `README.md`、`AGENTS.md`、`docs/HANDOVER.md`）。

## [v2.30] · 2026-09-04 · P10 未覆盖项补充范围登记

- **补充范围**：登记基础敏感词拦截、事务提交后原生 WebSocket 通知、Chromium/Firefox/WebKit 跨浏览器回归、读屏/键盘专项验收和正式压力基线；补充 Checklist 与证据报告不回改 v2.29 历史结果（影响 `docs/01-技术栈与总体架构.md`、`docs/02-API接口规范.md`、`docs/03-MVP实施与Docker部署.md`、`docs/05-后续开发路线图与实施手册.md`）。
- **依赖评估**：补充登记 `spring-boot-starter-websocket`，采用原生 WebSocket handler，不引入 STOMP broker；前端沿用浏览器原生 WebSocket，压测和浏览器验收复用现有 Node/Playwright 工具链。

## [v2.29] · 2026-09-04 · P10 评论与通知验收与文档闭环

- **功能完成**：新增一级评论/回复、同笔记复合外键、500/300 字限制、纯文本校验、20 条/分钟限流、作者软删除和已删除占位（影响 `backend/src/main/java/com/scenary/comment`、`V5__comments_notifications.sql`、`frontend/src/views/NoteDetailView.vue`）。
- **通知闭环**：点赞、关注、评论、回复在同一 MySQL 事务中生成通知；新增倒序游标、未读数、指定/全部已读和通知页面，未引入 outbox、队列或第三方依赖（影响 `backend/src/main/java/com/scenary/notification`、`SocialService.java`、`frontend/src/views/NotificationsView.vue`、`TopNav.vue`）。
- **验收闭环**：P10 黑盒 `17/17`、后端 JUnit `32/32`、前端 Vitest `21/21`、Vite `104 modules`、Playwright `1 passed`、Compose 重建/健康、Flyway V5 实查和浏览器只读流程通过；新增证据报告、学习笔记并同步 Checklist、HANDOVER、AGENTS、README 和路线图（影响 `docs/evidence/2026-09-04-P10评论与通知验收.md`、`docs/learning/16-P10评论通知事务与软删除.md` 及关联文档）。

## [v2.28] · 2026-09-04 · P10 评论与通知立项

- **契约先行**：固定评论按 `id ASC` 正序游标、通知按 `id DESC` 倒序游标；新增评论/回复、软删除、通知分页和批量已读接口，详情评论区与导航未读数进入 P10 范围（影响 `docs/02-API接口规范.md`、`docs/05-后续开发路线图与实施手册.md`）。
- **数据与事务决策**：登记 Flyway V5 的 `comments`、`notifications` 设计；用复合外键限制回复只能落在同一笔记，评论/点赞/关注与通知先采用同库同步事务，不新增 outbox、队列或第三方依赖（影响 `docs/01-技术栈与总体架构.md`）。

## [v2.27] · 2026-09-04 · P9 社交最小闭环验收与文档闭环

- **功能完成**：新增点赞/取消点赞、收藏/取消收藏、关注/取消关注及“我的收藏”游标列表；详情、Feed、公开主页返回统一社交状态，前端补齐详情/卡片/主页/收藏入口与匿名登录回跳（影响 `backend/src/main/java/com/scenary/social`、`frontend/src/api/social.js`、`frontend/src/views`、`frontend/src/components`）。
- **数据与并发**：Flyway V4 新增三张关系表，以唯一键、外键和自关注 CHECK 兜底；关系表聚合计数，P9 黑盒 20 并发点赞/关注和权限矩阵通过（影响 `backend/src/main/resources/db/migration/V4__social_relations.sql`、`SocialMapper.xml`、`docs/dev/test-p9-social.mjs`）。
- **验收闭环**：P9 黑盒 `13/13`、后端 JUnit `26/26`、前端 Vitest `15/15`、Vite `101 modules`、Compose 配置/健康和浏览器只读主流程通过；新增 P9 证据、学习笔记并同步 03 Checklist、HANDOVER、AGENTS、README 和路线图（影响 `docs/evidence/2026-09-04-P9社交最小闭环验收.md`、`docs/learning/15-P9社交关系表与幂等写入.md` 及关联文档）。

## [v2.26] · 2026-09-04 · P9 社交最小闭环立项

- **契约先行**：确认 P8 已通过全部出口门禁，P9 进入实现；02 新增点赞、收藏、关注、“我的收藏”接口及详情/Feed/主页社交状态契约（影响 `docs/02-API接口规范.md`、`docs/05-后续开发路线图与实施手册.md`）。
- **数据方案**：登记 V4 三张关系表，使用唯一键/外键/CHECK 保证幂等、引用完整性和禁止自关注；计数以关系表聚合，不依赖预留 `notes.like_count`（影响 `docs/01-技术栈与总体架构.md`）。

> 维护规约见 [AGENTS.md §4.1](AGENTS.md)：满足触发条件必须追加条目；历史条目不改写，错误用勘误行修正。

## [v2.25] · 2026-09-03 · P8 验收数字校正与文档闭环

- **验收勘误**：补录并发幂等回归后 Docker Maven 测试实际为 `21/21`，同步当前证据、HANDOVER 和学习笔记；历史版本条目保留不改写（影响 `docs/evidence/2026-09-03-P8改进验收.md`、`docs/HANDOVER.md`、`docs/learning/14-P8验收证据与异步状态.md`）。
- **原子提交**：P8 功能、测试和运维改动已拆为认证、媒体、笔记、traceId、前端请求/上传、前端页面、后端测试、前端测试和运维提交；本条关联 `852d8c0`、`d959bbe`、`2223bc4`、`96f259d`、`65378c2`、`ec046e1`、`2e72b8b`、`770ba87`、`5e489be`。
- **状态闭环**：AGENTS、README、03 Checklist、HANDOVER、CHANGELOG、API/架构/部署文档、证据和学习索引已对齐，P8 保持完成，P9 保持未启动。

## [v2.24] · 2026-09-03 · P8 范围审阅与原子提交闭环

- **审阅修正**：补齐发布页草稿离开保护、提交中禁止导航、退出登录后的守卫放行和图片排序按钮无障碍标签；前端回归由 11/11 增至 12/12（影响 `frontend/src/views/PublishView.vue`、`frontend/tests/PublishView.spec.js`）。
- **文档收口**：同步 P8 证据中的 Playwright、迁移预览、实际未覆盖边界和前端断言数量；修正异步失败“人工标记 status=2”、部署示例未固定镜像和前端 lint 门禁表述（影响 `docs/evidence/2026-09-03-P8改进验收.md`、`docs/01-技术栈与总体架构.md`、`docs/02-API接口规范.md`、`docs/03-MVP实施与Docker部署.md`、`docs/learning/07-RabbitMQ手动ack与DLX三件套.md`、`docs/HANDOVER.md`）。
- **提交治理**：P8 工作树已按认证、媒体、笔记、前端、测试、运维和文档边界完成中文 conventional commit；历史媒体 URL 仅执行 `-Preview`，正式批量迁移仍由部署运维执行（影响 `ops/migrate-media-urls.ps1`、P8 证据与交接文档）。

## [v2.23] · 2026-09-03 · P8 全量验收与文档闭环

- **验收完成**：Compose 黑盒认证/拦截/媒体/缩略图/端到端为 `18/8/15/7/30`，P8 集成矩阵 `6/6`，合计 `84/84`；P8 出口门禁通过，P9 保持未启动（关联 `docs/evidence/2026-09-03-P8改进验收.md`、`docs/03-MVP实施与Docker部署.md`）。
- **测试门禁**：Docker Maven JUnit `20/20`、前端 Vitest `11/11`、Vite 构建、两份 Compose config、tracked/全工作树敏感信息扫描和 `git diff --check` 均通过（影响 `backend/src/test`、`frontend/tests`、`.github/workflows/ci.yml`）。
- **运维证据**：备份恢复后 users/notes/media 为 `147/81/213` 且与源库一致，不存在数据库恢复返回退出码 1，临时库已清理（影响 `ops/restore-mysql.ps1`、P8 证据报告）。
- **浏览器回归**：使用用户提供图片完成注册→上传→发布“月下宫墙”→详情→删除后只读核验；390/768/1440 首页、登录、公开详情无水平溢出且 console error=0（影响 P8 证据报告、`docs/HANDOVER.md`）。
- **文档沉淀**：新增 P8 全量证据和第 14 篇学习笔记，并同步 Checklist、HANDOVER、README、AGENTS 与学习索引；工作树仍未提交（影响 `docs/evidence/2026-09-03-P8改进验收.md`、`docs/learning/14-P8验收证据与异步状态.md` 及关联文档）。

## [v2.22] · 2026-09-03 · P8 Compose 复验中间 checkpoint

- **验收发现**：最新容器复验中，认证脚本受持久化 `rl:register` 测试计数影响而中止；上传脚本对 `status=0` 尚未生成的缩略图立即匿名读取，时序断言不成立；数据库集成脚本按索引列数误计双列唯一键。当前 P8 不标记完成，待修正验收口径并串行重跑（影响 `docs/dev/test-media36.mjs`、`docs/dev/test-p8-integration.mjs`、`docs/HANDOVER.md`）。
- **最新状态**：认证 18/18、拦截链 8/8、媒体 15/15、缩略图 7/7、Compose 集成 6/6 已通过；端到端在公开/私密详情作者视角处失败并中止，正在定位（影响 `docs/dev/test-e2e38.mjs`、详情查询实现）。
- **根因与修复**：`NoteService` 对 `Long` 用户 ID 使用引用比较，ID 大于 127 时误判作者视角；改为值比较并新增详情单测，端到端脚本同步改为安全断言后待重建容器复验（影响 `backend/src/main/java/com/scenary/note/NoteService.java`、`backend/src/test/java/com/scenary/note/NoteServiceTest.java`、`docs/dev/test-e2e38.mjs`）。
- **门禁修复**：CI 敏感信息扫描的正则以连字符开头，改用 `git grep -e` 显式传递模式，避免扫描命令被误解析为选项；待重新执行等价扫描（影响 `.github/workflows/ci.yml`）。

## [v2.21] · 2026-09-03 · P8 后端止血与配置收口

- **认证**：`AuthService.register` 捕获 `DuplicateKeyException` 并回 `41001`，并发注册不再落 500；新增单测覆盖唯一键冲突翻译。
- **发布**：`POST /notes` 新增可选 `requestKey` 幂等键，重复提交返回同一篇笔记；媒体绑定改为 `note_id IS NULL` 条件更新并校验影响行数，绑定失败直接回滚。
- **媒体**：缩略图消费者在最终失败后写入 `status=2`、失败原因与时间，再进入 DLQ；状态查询与详情展示改为按配置返回展示 URL，默认不暴露原图。
- **配置与部署**：`backend/run-dev.sh` 改为缺少必需 env 即失败；`application-dev.yml` 去掉 secret 默认值；`docker-compose*.yml` 固定 MinIO 镜像版本并补 Redis 依赖检查。
- **验收**：新增 `backend/src/test/java/com/scenary/{auth,note,mq}` 三组单测，`cd backend && mvn -q test` 13/13 通过；`cd frontend && npm run build`、`docker compose config --quiet`、`docker compose -f docker-compose.middleware.yml config --quiet` 均通过；证据已归档至 `docs/evidence/2026-09-03-P8后端止血与配置收口.md`。

## [v2.20] · 2026-09-02 · 补齐 P8 学习笔记

- **学习沉淀**：新增 `docs/learning/13-MVP审计与质量门禁.md`，记录从 happy path 复验转向状态/并发/权限生命周期/外部故障/用户可理解性审计的方法、验证证据和可复用结论。
- **索引同步**：`docs/learning/00-学习笔记索引.md` 登记第 13 篇，关联 P8 改进阶段。
- **状态说明**：P8 仍处于改进中；前端首批止血已通过 `npm run build`，后端 P0 和完整 P8 证据报告尚未完成，Checklist 不勾选。

## [v2.19] · 2026-09-02 · MVP 审计与后续开发文档立项

- **审计**：前端与后端子 agent 完成代码及实测审计，新增 `docs/frontend-audit-2026-09-02.md`；确认动态路由复用、登出未撤销 refresh、私密内容可被黑名单 access 读取、媒体绑定竞态、刷新旋转非原子、MQ 失败永久处理中、对象/资源泄漏、配置与测试门禁等 P0/P1 问题。
- **改进总纲**：新增 `docs/04-产品与工程改进总纲.md`，将问题编号化并拆成 P8-01~P8-22，定义前端视觉/交互/无障碍、后端可靠性、CI/备份/对象存储和证据验收门槛；当前实现 agent 以该文档为唯一施工入口。
- **后续路线**：新增 `docs/05-后续开发路线图与实施手册.md`，详述 P9 社交、P10 评论通知、P11 搜索、P12 视频地点、P13 内容安全、P14 规模化的 API/数据/前端/后端/运维/验收与回滚要求；P8 未通过前不启动后续功能。
- **状态同步**：README、AGENTS、HANDOVER 增加文档入口并将下一步改为 P8 P0 止血；未修改 02 API 契约或现有实现代码。

## [v2.18] · 2026-09-02 · 补充后端 JUnit 单元测试

- **测试**：新增 `backend/src/test` 下 3 个 JUnit 5 测试类、9 个纯逻辑用例，覆盖 JWT 签发/解析/身份窥探、图片魔数识别与读取窗口、游标分页边界；`cd backend && mvn -q test` 退出码 0，无需 Docker 或外部中间件。
- **边界更新**：MVP 证据报告与 HANDOVER 测试矩阵改为记录 JUnit 9/9；数据库交互和跨模块编排仍由 Docker 黑盒脚本覆盖，完整 Repository 集成测试暂未纳入。

## [v2.17] · 2026-09-02 · MVP 证据链复验与门禁

- **运行复验**：Compose 全栈重建并启动成功，backend/MySQL healthy；认证、拦截、上传、端到端四组黑盒脚本经 `:8081` 入口取得 18/8/13/29，共 68/68 PASS；前端构建及浏览器首页/登录页、console error 检查通过。
- **证据缺口修复**：`test-thumbnail37.mjs` 原先固定依赖宿主 `:15672`，与生产 Compose 仅暴露 `:8081` 冲突；新增管理 API 不可达时的 Compose 容器回退，不新增依赖或端口。
- **证据归档**：新增 `docs/evidence/README.md` 和 `docs/evidence/2026-09-02-MVP复验.md`，记录验证对象、环境、命令、结果与未覆盖边界；同步更新 AGENTS、03 Checklist 与 HANDOVER。
- **治理门禁**：二期 A 起每个 Phase/功能环节必须提交可复现证据报告，并与 Checklist、HANDOVER、CHANGELOG 相互引用；证据缺失或必需验收不可重跑时不得标记完成。

## [v2.16] · 2026-09-02 · 补齐 Phase 7 可执行验收

- **缺陷修复**：`backup-mysql.ps1 -Preview` 不再向同名 Switch 参数赋值，且预览模式不创建目录；实际备份改为将数据库名安全地作为容器内 shell 参数传递，并通过容器环境变量提供 MySQL 密码，避免落入进程参数。
- **部署适配**：`check-dlq.ps1` 改由 `docker compose exec rabbitmq rabbitmqctl` 查询队列深度，不再依赖未对外映射的 RabbitMQ 管理端口或本机环境变量。
- **缓存修复**：端到端复验暴露 `PageResult` 缺少 Jackson 反序列化入口，导致首页 L1 缓存持续降级为数据库查询；为其不可变构造方法补充 JSON 构造标记，恢复读缓存路径。
- **缓存一致性**：首页 L1 改为版本化键；发布在事务前、提交后推进版本，抢在提交窗口读到旧数据的请求即使晚写入也不会被后续读取命中。L1 仅用于默认 10 条首页，带 `limit` 的查询不再错误复用该缓存。
- **缓存校验**：L1 命中会以首页覆盖索引的笔记 ID 序列校验当前性；发现交错写入的历史页即丢弃重建，保留卡片聚合缓存收益而不向用户返回旧首页。
- **测试安全性**：四组 API 验收脚本支持 `SCENARY_API_BASE_URL`，改为动态生成临时账号密码；缩略图脚本的 MQ 凭据仅从未跟踪的 `.env` 或进程环境读取。
- **验收与文档**：全栈 Compose 重建后 backend/MySQL 健康、frontend `:8081`、`/api/v1/ping`、备份预览及 DLQ 深度巡检均通过；03 Checklist 补记 P7 验收项，并同步 README 与 HANDOVER。

## [v2.14] · 2026-08-31 · Phase 6 全栈容器化验收完成
## [v2.15] · 2026-09-01 · Phase 7 运维基线三件套落盘
- **实现**：新增 `ops/backup-mysql.ps1`、`ops/watch-backend-errors.ps1`、`ops/check-dlq.ps1`，分别覆盖 MySQL 备份、后端错误日志过滤与 `media.dlq` 深度巡检；备份输出默认落到 `backups/` 并已加入 `.gitignore`。
- **文档同步**：更新 `docs/03-MVP实施与Docker部署.md` 的 Phase 7 说明、`docs/HANDOVER.md` 状态与待办、`README.md` 状态摘要、`AGENTS.md` 快照。
- **学习沉淀**：新增 `docs/learning/12-Phase7运维基线三件套.md` 并登记索引；Phase 7 进入完成态，下一步转入二期A 预研。

- **验收**：`docker compose config --quiet`、镜像构建和全栈启动通过；backend health、Redis、MySQL health、RabbitMQ、MinIO 与 frontend 运行正常，唯一对外端口为 `:8081`。
- **冒烟**：经 Nginx `/api` 完成 ping、注册、JWT TTL、上传、缩略图 status=1、发布和匿名 feed；`/minio` 缩略图反代及 SPA history 路由均返回 200，浏览器首页新图片可见且无控制台 error。
- **文档与学习**：勾选 03 的 6.5/6.6/README Checklist，更新 README、AGENTS、HANDOVER，新增 learning/11；Phase 7 保持未开始。开发期旧媒体直连 URL 的迁移注意事项记入 HANDOVER。

## [v2.13] · 2026-08-31 · 修复 Phase 6 MinIO 图片反代优先级

- **缺陷修复**：全栈冒烟确认普通 `/minio/` 前缀 location 会被静态图片正则抢占，缩略图 URL 返回 404；Nginx 改用 `^~ /minio/` 固定走对象存储反代。
- **文档同步**：`docs/03-MVP实施与Docker部署.md` 的 6.3 示例标注优先级原因；重新构建 frontend 后继续完成 6.6 验收。

## [v2.12] · 2026-08-31 · 补齐 Phase 6 生产 profile 基础配置

- **缺陷修复**：全栈冒烟将 `prod` profile 下缺失的 MyBatis XML 路径暴露为 `Invalid bound statement`；Compose 显式传入 `MYBATIS_MAPPER_LOCATIONS` 与下划线映射配置。
- **部署完整性**：同时传入 RabbitMQ 手动确认/并发/prefetch、multipart 限额，保证容器运行语义与已验收的本地开发配置一致；重新构建后继续 6.6 冒烟。

## [v2.11] · 2026-08-31 · 修复 Phase 6 生产配置绑定与 Redis 启动

- **缺陷修复**：全栈首次启动日志确认 Redis 参数被错误交由 shell 执行、后端生产 profile 未绑定 `scenary.jwt.*`；`docker-compose.yml` 改由 `redis-server` 接收参数，并显式传入 `SCENARY_JWT_SECRET/ACCESS_TTL/REFRESH_TTL/ISSUER`。
- **文档同步**：`docs/03-MVP实施与Docker部署.md` 修正 6.4 Compose 样例与 relaxed binding 说明；6.5/6.6 验收仍待本次重新构建后确认。

## [v2.9] · 2026-08-31 · Phase 6 容器化文件落盘

- **实现**：新增 `backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx.conf`、根 `docker-compose.yml` 及构建上下文 `.dockerignore` 文件，按 03 §6 编排中间件、后端、前端和 `/api`、`/minio` 反代。
- **验证边界**：Compose 静态配置检查通过；实际 `docker compose build/up` 因当前环境 Docker 构建审批被拒绝，6.5/6.6 保持未勾选，未宣称 Phase 6 完成。
- **状态同步**：更新 `AGENTS.md` 快照和 `docs/HANDOVER.md` 待办；Phase 7 暂不开始。

## [v2.10] · 2026-08-31 · 修复后端容器健康检查依赖

- **缺陷修复**：`eclipse-temurin:21-jre-alpine` 运行镜像显式安装 `wget`，使 Compose backend healthcheck 的 HTTP 探测可执行；同步更新 03 §6.1 示例。
- **交接更新**：`docs/HANDOVER.md` 修正 Phase 5 已完成的工作边界，并记录健康检查依赖。

## [v2.8] · 2026-08-31 · Phase 5 联调完成

- **联调验收**：`mvn compile` 通过；后端 ping、Vite 首页及 `/api` 代理返回 200；auth/interceptor/media/thumbnail/e2e 五套脚本共 75 条断言全通过。
- **性能观察**：三图 PNG+WebP 上传约 76ms，三张图片处理完成约 266ms。
- **问题记录**：受限环境 Maven 本地仓库写权限导致的误报已定位并沉淀至 `docs/learning/10-Phase5联调与Maven本地仓库权限.md`。
- **状态同步**：更新 `docs/03-MVP实施与Docker部署.md` Checklist、`AGENTS.md` 快照、`docs/HANDOVER.md` 和学习笔记索引。

## [v2.7] · 2026-08-29 · 交接文档维护规则明确

- **治理规则**：`AGENTS.md` 新增 HANDOVER 持续维护约束，明确阶段/验收/运行方式/checkpoint 变化后的同步要求及三大设计文档的职责边界。
- **交接状态**：更新 `docs/HANDOVER.md` 为 v2.7，记录 P4 后 checkpoint 和“暂不开始 Phase 5”的工作边界；README 同步标注 HANDOVER 为持续维护文档。

## [v2.6] · 2026-08-29 · P4 浏览器验收完成与发布页缺陷修复

- **P4 验收**：真实浏览器剧本通过——A 注册并发布 3 篇（含 WebP），登出后 B 注册浏览/强刷详情、B 无删除入口，A 删除首页可见笔记后刷新列表消失。
- **缺陷修复**：`frontend/src/views/PublishView.vue` 补充 `reactive` 导入，修复选择图片后上传流程抛出 `ReferenceError` 导致无法发布。
- **状态同步**：更新 `docs/03-MVP实施与Docker部署.md` Checklist、`AGENTS.md` 快照、`README.md` 状态和 `docs/HANDOVER.md` 待办。

## [v2.5] · 2026-08-27 · 双主题重构 + 功能测试回归全绿 + 交接文档建立

- **主题**：前端配色切换为 浅薄荷绿(品牌阶)/青冥(夜间底与点缀)/茶白(日间纸底)；支持手动黑夜模式——`@custom-variant dark` 重绑 html.dark、语义 token（paper/surface/mute/line/ink）双层映射、index.html head 内联防闪烁预判、TopNav 新增 ☀️/🌙 切换钮并记忆 localStorage。`npm run build` 门禁复跑通过。
- **功能测试（API 级）**：五套脚本共 **75 断言全部 PASS**。修复两处可重入性缺陷——脚本写死用户名导致二次执行撞重名/登录锁残留（改为随机后缀）；test-interceptor35 断言从 3.5 占位桩升级为 3.8 真实 UserVO 契约形态。
- **验收夹具**：新增 `docs/dev/fixtures/`（真实 webp 82KB + 两张程序生成 PNG），smoke-backend.http 与 P4 剧本引用就位。
- **交接文档**：新建 [docs/HANDOVER.md](docs/HANDOVER.md)（现状/跑法/测试矩阵/待办/坑位速查），README 文档索引与 AGENTS 阅读顺序同步登记。
- **学习笔记**：成篇 `09-TailwindV4手动暗黑模式三件套.md`（L19）；L20 记录测试可重入修复实录（未成篇）。
- **遗留待办**：P4 浏览器人工/自动化剧本仍未执行（Checklist 对应行保持未勾）。

## [v2.4] · 2026-08-27 · Phase 4 前端六步实现落盘（验收剧本待执行）

- **落盘范围（4.1~4.6）**：视觉基底（陶土橙 @theme 变量/system-ui/清空模板 demo）；基础设施（utils/request.js 含 401 单飞刷新+重放+40100 回登录、stores/user.js localStorage 持久化、全路由守卫 guestOnly/requiresAuth、api 五模块对齐 02 契约）；LoginView 双 Tab 客户端校验；TopNav 发布笔形按钮+头像下拉菜单；HomeView 双列瀑布流(骨架屏 8 卡+IntersectionObserver 哨兵)；PublishView ≤9 张即传即预览(处理中转圈/失败红标可重传/左右移排序/公开私密 radio/标题计数器)；ProfileView 信息卡+编辑弹层+九宫格(本人视角含私密徽章)；NoteDetailView 纵向大图流+作者卡+作者删除钮；NotFound 404 态。
- **门禁与冒烟**：`npm run build` 通过（路由分包 486ms）；dev server HTTP 200 且经 `/api` 代理取到后端 ping 包络——前后端联通链路就绪。过程中修复 main.css 残留 base.css import 的构建失败。
- **待办（守则 3 记录不跳过）**：P4 手动验收剧本未执行（注册 A 发 3 篇含 webp→登出→B 浏览/强刷保持登录态/B 无删入口/A 删文消失、控制台无红错）。留待下轮以浏览器自动化或人工执行后补勾 Checklist。
- 模板遗留文件已删除（HelloWorld/TheWelcome/icons/counter/AboutView/logo.svg/base.css）。

## [v2.3] · 2026-08-27 · Phase 3-3.8 完成暨 Phase 3 后端整体收官

- **验收状态**：Checklist 3.8/3.9 勾选。`docs/dev/test-e2e38.mjs` **29 用例全 PASS**，18 个契约端点全部实装：资料读写/头像(200x200 居中裁切)/个人主页与网格(可选令牌视角差异)/发布事务(bind 回填 order_no, cover=首图 thumb)/详情聚合(mine 判定/有序 images)/软删幂等/feed 游标+两级缓存(L1 整页 JSON TTL300 写穿透失效; L2 卡片 TTL1h 命中免回表)。
- **契约补充**：docs/02 发布接口原文缺少"创建私密笔记"入口而 §3.5 引用私密语义——`POST /notes` 增加可选 `visibility`(缺省 1，仅允许 0/1)。属契约缺口补丁，请知悉。
- **缺陷修复**：NoteMapper.xml 初版 INSERT 漏 visibility 列导致私密笔记得以公开访问——被 ⑨c/⑨d 用例当场抓获，修复后全套复跑通过。feed L1 首读存在一次极小概率的写侧可见性竞态（断言已按"至多一次竞态后字节稳定"语义固化）。
- **结构决策**：跨包视图升 common（AuthorVO/GridCardVO）；个人网格数据经 NoteService 门面供数给 user 模块（依赖铁律落地样板）；JwtUtil 增加 peekUserId 供公开端点"登录则增强"。
- **验收件归档**：`docs/dev/smoke-backend.http`（全端点 IDEA 格式正反例）+ 5 个可编程脚本（共 75 断言）。
- **学习笔记**：backlog L6 到点成篇 `docs/learning/08-游标分页vsOffset分页.md`。

## [v2.2] · 2026-08-27 · Phase 3-3.7 缩略图管线闭环完成

- **验收状态**：Checklist 3.7 勾选——`docs/dev/test-thumbnail37.mjs` 7 用例全 PASS：上传 1000x604 约 800ms 轮询至 status=1；缩略图 800x483 限边等比，300x200 小图不被放大；thumb 直链匿名可读（jpeg 7683B）；毒消息两类（malformed JSON / 幽灵 mediaId）均经本地两次重试后 nack 落 `media.dlq` 深度 0→2；毒消息倾泻后正常流不受阻。
- **证据存档**：消费者日志含 success/malformed-dead-lettering/attempt1..2/dlq 四类行；`mc ls` 见 thumb/202608/ 与原图同 uuid 成对 `_t.jpg`；DB 回写 status=1 + 宽高 + thumb_object_key。历史遗留：media id=6 永久 status=0，系其消息在队列绑定前发出被无声丢弃（3.6 注记的活案例），人工 DELETE 处理。
- **拓扑落盘**：RabbitConfig 扩展 media.thumbnail.q(带 x-dead-letter 参数)/media.dead exchange/media.dlq 及两条 binding；消费者 `mq/ThumbnailConsumer`（手动 ack、解析容错、本地重试 2 次、重编码 JPEG q0.8 剥 EXIF）。yml 的 concurrency=2/prefetch=1/manual 三项已被运行时验证（consumers=2）。
- **学习笔记**：backlog L5 到点成篇 `docs/learning/07-RabbitMQ手动ack与DLX三件套.md`。

## [v2.1] · 2026-08-27 · Phase 3-3.6 上传管线完成

- **验收状态**：Checklist 3.6 勾选——`docs/dev/test-media36.mjs` 13 用例全 PASS（无 token 拦截/双图上传/契约 TTL 形态/匿名对象回读/魔数欺骗拒绝/超限 400/越权 40300/不存在 40400/轮询形态/游离媒体删除闭环）。三项手册"眼见为实"逐一实证：media 表 4 行 status=0、mc 列出 orig/202608/ 全部对象、临时探测队列收到 `{"mediaId":...}` 字节级匹配。
- **统计怪癖注记**：RabbitMQ 管理 API 的 exchange publish_count 在本环境恒为 0（连管理 API 自发直投也不计），消息已发的证据改用队列深度/字节数替代；已知不影响功能。
- **契约修正**：上传响应实现初版误把数组直接作 data——测试断言当场抓获，按 docs/02 §4.1 补 `MediaUploadVO{items}` 包装归位。"响应形状差异能被黑盒断言逮住"验证了 smoke 脚本方法论。
- **落盘**：`config/{MinioProperties,MinioConfig,RabbitConfig(交换机+JSON模板)}`、`media/{MinioService,MediaImageType,MediaEntity,MediaMapper(+XML),MediaService,MediaItemVO,MediaUploadVO,MediaController}`。涵盖 §4.3 游离媒体删除（早于 3.8 收口，模块自洽）。
- **学习笔记**：新增 `docs/learning/06-Content-Type不可信与魔数嗅探.md`（L18）。

## [v2.0] · 2026-08-27 · Phase 3-3.5 认证拦截链完成

- **验收状态**：Checklist 3.5 勾选——脚本 `docs/dev/test-interceptor35.mjs` 8 用例全 PASS：无 token 与非 Bearer 方案 401/40100；垃圾 JWT 40101；有效 access 经 UserController(/me 占位)回显正确 userId；refresh 当 access 用 40101；**已登出 access 命中黑名单 40101"令牌已登出"**（双保险在真实请求链路生效）；同会话连发两请求无串号。
- **语义决策**：携带了 type=refresh 的令牌访问受保护端点属于"令牌无效(40101)"而非"未登录(40100)"——前者是凭证本体失效，后者指根本未携带，与 docs/02 §1.2 行语义对齐。
- **设计注记**：Spring 拦截器注册模式无法表达方法条件，契约中笔记详情的公开读由 AuthInterceptor 内部豁免（仅限 GET 且路径为 /notes 或 /notes/{id}）；`JwtUtil.stripBearer` 从 AuthService 上移至 JwtUtil 统一持有。UserController 当前为 /me 占位实现（回显 stage 字段），完整契约在 3.8 落地。
- **落盘**：`auth/UserContext`、`auth/AuthInterceptor`、`config/WebConfig`、`user/UserController`(占位)。

## [v1.9] · 2026-08-27 · Phase 3-3.4 认证全套完成

- **验收状态**：Checklist 3.4 勾选——验收脚本 `docs/dev/test-auth34.mjs`（Node fetch 直驱，绕开 Git Bash GBK 控制台对命令行中文的转码坑，见 L17 待写条目）18 用例全 PASS：注册(中文昵称回显/TTL=7200/2592000)、重名 41001、非法用户名 40000、统一文案错密提示、刷新旋转后旧令牌重放 40101、登出吊销白名单后再刷 40101、连错 5 次 42001 锁定且锁内正确密码同拒。
- **契约留白决策**：docs/02 错误码表未定义"用户名或密码错误"语义——落位 `40000` + 固定文案"用户名或密码错误"（防账号枚举），不新造错误码；后续如需独立码须先修契约。
- **落盘**：`config/JwtProperties`、`auth/{JwtUtil,AuthService,AuthController,RegisterRequest,LoginRequest,RefreshRequest,AuthVO}`。Redis 键清单即 docs/01 §6 预定：`auth:refresh:*` 白名单 / `auth:access:bl:*` 登出黑名单 / `rl:register:*`、`rl:loginfail|lock:*` 限流。登出按文档"双保险"实现：SCAN 前缀删该用户全部白名单键 + access jti 黑名单 TTL=剩余寿命。
- **学习笔记**：新增 `docs/learning/05-JWT双令牌与Redis白名单吊销.md`（backlog L1 到点成篇）、索引补 L17。

## [v1.8] · 2026-08-27 · 缺陷修复与方法不匹配语义纠正 + 3.3 user 基础

- **行为确认**：项目所有者报告访问 `http://localhost:8080/` 返回 `40400 资源不存在`——经全路径回归确认属**契约内设计行为**（后端不出静态页，未映射路径统一业务包络 404+40400，见 docs/02 §1.1/§1.2）。
- **缺陷修复**：回归中发现 GET-only 端点受 POST/PATCH 访问时被兜底异常捕获，误报为 `500/50000 服务开小差了`。已为 HttpRequestMethodNotSupportedException / HttpMediaTypeNotSupportedException 增设映射：HTTP 405/415 + 包络 `40000`（客户端用法问题≠服务端故障）。复测全绿。
- **3.3 落盘**：`user/UserEntity`、`user/UserMapper` + `resources/mapper/UserMapper.xml`（insert 主键回填/按用户名与 id 查询/资料动态 set/头像更新）、`config/PasswordConfig`(BCrypt cost=10)。启动验证：此前 "No MyBatis mapper was found" 警告消失（扫描命中），XML 解析无错；功能验收依手册由 3.4 四连 curl 覆盖。
- 附注：后台任务强杀 java 监听进程导致旧 mvn 任务退出码 1 属预期现象（运维侧已知）。

## [v1.7] · 2026-08-27 · Phase 3 前两步完成（3.1 Flyway / 3.2 common+ping）

- **验收状态**：3.1 Flyway 首迁 `Successfully applied 1 migration`，users/notes/media 三表实查就绪；3.2 `/api/v1/ping` 返回契约包络。附带完成 Flyway 纪律亲测实验：篡改已应用 V1 → checksum mismatch 拒启 → 还原 → validate 通过。
- **配置决策**：`application-dev.yml` 的 JWT/MinIO 密钥改为 `${ENV:无害默认}` 占位符——真实值由新增的 `backend/run-dev.sh` 从根 `.env` 逐行导入后注入（不能直接 source：compose 风格值含空格会被 bash 拆词）；run-dev.sh 另含 JDK≥21 版本守卫（长生命周期终端可能仍持旧 JAVA_HOME）。（影响 `backend/src/main/resources/application-dev.yml`、`backend/run-dev.sh`）
- **踩坑记录**：停止 `mvn spring-boot:run` 后台任务时其派生 java 进程会成孤儿继续占用 8080，需按监听端口找 PID 强杀后再重启；该现象记入学习索引 L16 待写。
- **落盘**：V1__init.sql（01 §6 全量 DDL）、common 包五类（Result/ErrorCode/BizException/GlobalExceptionHandler/PageResult）与 PingController；错误码表逐条对齐 02 §1.2。
- **学习笔记**：新增 `docs/learning/04-Flyway版本化迁移纪律.md` 并登记索引。

## [v1.6] · 2026-08-27 · Phase 2 中间件先行完成

- **验收状态**：P2 完成——`docker compose -f docker-compose.middleware.yml up -d` 全部 running：MySQL 8.4.11 healthy 且 scenary 业务账号可连 `scenary` 库；Redis RESP 应答 +PONG；RabbitMQ 3.13.7 管理 API 用 scenary_mq 账号可达；MinIO 健康 200，一次性初始化容器建成 `scenary-media` 桶并设 download 匿名权限（BUCKET_READY）。
- **落盘**：`.env.example`（模板，含 PUBLIC_HOST）与 `docker-compose.middleware.yml`（按 03 手册原文）；本地 `.env` 填入 Phase 0 生成的 JWT_SECRET(hex48)/MINIO 密码(hex16)，已验证被 .gitignore 忽略、未入库。（影响仓库根）
- **安全注记**：MySQL healthcheck 按 03 原文内插根密码，实测明文进容器元数据（`docker inspect` 可见）——单机开发接受并记入学习笔记 [03]，公网部署前应改造（详见笔记"可复用结论"2）。
- **学习笔记**：新增 `docs/learning/03-Compose变量注入的时机与明文泄露面.md` 并登记索引 L15。

## [v1.5] · 2026-08-27 · Phase 1 工程脚手架完成

- **验收状态**：P1 完成——后端 `mvn -q compile` 通过（Boot 3.5.16/Java 21）；前端 Vite 8 dev server HTTP 200 无报错；git `main` 三笔原子提交（治理文档/后端/前端）。
- **后端落盘**：手写 `backend/pom.xml`（文档依赖清单全量：web/validation/actuator/data-redis/amqp/mybatis3.0.5/pagehelper2.1.0/flyway(+mysql)/mysql-j/jjwt0.12.6/security-crypto/minio8.5.17/thumbnailator0.4.20/lombok/test）；`ScenaryApplication`；8 个业务包以 package-info.java 固化（common/config/auth/user/media/note/feed/mq）。（影响 `backend/*`）
- **前端落盘**：create-vue(router+pinia 纯 JS) + axios + TailwindCSS4(@tailwindcss/vite)；vite.config.js 增加 tailwind 插件与 `/api → localhost:8080` 代理；main.css 首行引入 tailwind。（影响 `frontend/*`）
- **修正**：误入库的安全钩子运行态文件 `frontend/.mimosa/**` 已通过 amend 移出跟踪，根 .gitignore 新增 `.mimosa/`。
- **遗留提示**：本仓库配置的 Mimosa 提交钩子报告"完整安全扫描结论缺失"（按兼容策略放行）；按其技能调用约束需项目所有者显式发起深度扫描，暂记录不阻塞施工。
- **学习笔记**：新增 `docs/learning/02-npm脚手架CLI在非交互环境下的两个坑.md` 并登记索引 L14。

## [v1.4] · 2026-08-27 · 勘误并修订 v1.3 决策：回退 Boot 3.5.x + 脚手架去平台化

- **勘误**：v1.3 的升级决策撤回。理由：项目所有者补充了两条约束——①出现部署/版本选择问题时优先回退保守线；②脚手架不使用 start.spring.io。经核实 MyBatis 4.0.x/PageHelper 4.x 属刚换轨的新生态（踩坑面大），判定违背"优先稳妥"取向。
- **现行决策**：Boot 基线回到 3.5 线最新补丁 **3.5.16**（Maven Central 可用）；脚手架改为**本地手写 pom.xml**，不再调用 start.spring.io（初始化器已下架 3.5 线且属外部服务，不可依赖）；文档原依赖清单（含 PageHelper 2.1.0）全部恢复有效。
- 影响文件：[docs/01 §3.1](docs/01-技术栈与总体架构.md)、[docs/03 Phase1](docs/03-MVP实施与Docker部署.md)、README「技术栈一句话」。

## [v1.3] · 2026-08-27 · 架构决策变更：Spring Boot 基线升级 3.5.x → 4.1.x

- **决策**：start.spring.io 已下架 3.5 线（仅余 4.x），经项目所有者确认后，Boot 基线由 3.5.x 升级为当前稳定线 **4.1.x**；联动调整第三方案两条：mybatis-spring-boot-starter 3.x→**4.1.0**、pagehelper-spring-boot-starter 2.1.0→**4.1.1**（官方自 4.0.0 起与 Spring Boot 大版本对齐命名）。其余依赖不变。
- 影响文件：[docs/01 §3.1](docs/01-技术栈与总体架构.md)、[docs/03 Phase1](docs/03-MVP实施与Docker部署.md)、README「技术栈一句话」。

## [v1.2] · 2026-08-27 · Phase 0 环境验证完成

- **验收状态**：P0 完成——docker 29.6.1(Desktop 4.81)/compose v5.2.0/JDK 21.0.11/mvn 3.9.11/node 22.23.2/git 2.46.1 全部达标；Docker 引擎内存配额 7.6GiB≥6GB、WSL2 后端确认。（影响 `docs/03` Checklist）
- **环境决策**：本机默认 JDK 由 17.0.17 切换为既有的 ms-21.0.11（用户级+系统级 JAVA_HOME 与两级 PATH 条目共四处）；如其他项目依赖 Java 17 按同样四处指回即可回滚。ZCode 当前会话内仍需显式 `export JAVA_HOME` 前缀，重启应用后自动生效。详见 `docs/learning/01`。（影响机器环境配置）
- **镜像源核查**：npm=npmmirror、Maven=aliyun(mirrorOf *)、Docker=daocloud 等 4 个加速器均已预先就位，未做改动。
- **密钥生成**：JWT_SECRET(hex48) 与 MinIO 密码(hex16) 已生成备用（Phase 2 写入 .env，不入库；遗失重跑 openssl 生成即可）。
- **学习笔记**：新增 `docs/learning/01-Windows下切换JDK版本与环境变量作用域机制.md` 并登记索引 L13。

## [v1.1] · 2026-08-27 · 文档补强与审查修订

- **治理机制**：新增 `AGENTS.md`（工作代理入口）、本日志、`docs/learning/00-学习笔记索引.md`（含学习主题 backlog）。
- **01-技术栈与总体架构**
  - 原"明确不用"清单升级为「延后安装清单」：7 个组件逐一给出回装时机/改动面/代价评估（Spring Security、MyBatis-Plus、springdoc、ES、MongoDB、Sa-Token、微服务化）；
  - 新增 §4.1 分包风格讲解：分层制 vs 模块制目录对照、"模块内部是否还有 controller/service/dao"的正答、模块内组织规则（≤15 文件平铺靠后缀）、依赖方向铁律；
  - 工程约定补充：测试基调（smoke 脚本为底线）、MQ routing key 扩展约定（`media.event` 上只增 binding 不复用队列）、密钥统一 hex 格式。
- **02-API接口规范**：媒体 URL 来源说明——前端永远使用接口返回完整 URL，域名由后端 `SCENARY_MINIO_PUBLIC_HOST` 决定。
- **03-MVP实施与Docker部署**（审查修复）
  - 修复不一致：nginx.conf 此前缺少 `/minio/` 反代段但 6.4 说明默认引用方案 B → 已把反代段正式写入 nginx.conf；
  - `.env.example` 补缺失变量 `PUBLIC_HOST`；
  - 开篇说明两份 compose 并存为有意设计；密钥生成 base64→hex（防 shell/YAML 转义坑）；
  - 新增解释段：为什么无需 application-prod.yml（Spring relaxed binding 覆盖机制）；
  - 冒烟剧本补充测试图片来源说明；修正 Checklist 标题的错误计数（41→实际逐项勾选制）。

## [v1.0] · 2026-08-27 · 立项

- 完成四份基础文档：README、01 技术栈与总体架构、02 API 接口规范（18 端点）、03 实施与 Docker 部署手册（P0~P7 + Checklist）。
- 技术底座决策：JDK21/SpringBoot3.5/MyBatis/Flyway/JJWT/Thumbnailator/MinIO/RabbitMQ/Vue3+Vite+TailwindCSS/Nginx/Docker Compose。
- 背景：曾评估基于 Pixelfed（PHP/Laravel）二次开发同一产品设想，因技术栈不匹配弃用，勘察结论存档于 `../docs/pixelfed/`（其中改造可行性分析对二期演进仍有参考价值）。
## [v2.37] · 2026-09-04 · P12-E1 分片上传契约立项

- **契约先行**：API v1.5 新增视频上传会话、8MiB 预签名 PUT、断点恢复、合并校验、取消和过期清理；新增 40902/40903，影响 `docs/02-API接口规范.md`。
- **施工边界**：新增 P12-E1 Checklist 与 V8 会话/分片表设计，图片代理上传、V7 视频转码和播放隐私边界保持不变，影响 `docs/03-MVP实施与Docker部署.md`、`docs/05-后续开发路线图与实施手册.md`。
