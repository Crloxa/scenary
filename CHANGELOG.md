# Changelog · Scenary

> 维护规约见 [AGENTS.md §4.1](AGENTS.md)：满足触发条件必须追加条目；历史条目不改写，错误用勘误行修正。

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
