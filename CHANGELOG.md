# Changelog · Scenary

> 维护规约见 [AGENTS.md §4.1](AGENTS.md)：满足触发条件必须追加条目；历史条目不改写，错误用勘误行修正。

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
