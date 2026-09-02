# Scenary · 极简风景图文社区（MVP 立项）

> 工作代号 **Scenary**，源自 [../思绪.txt](../思绪.txt) 的产品设想第 1 条：以纯风景图片/视频为内容的小红书式分享社区。
> 本仓库已完成 Phase 7 运维基线与前序全栈验收；当前 MVP 可通过 Docker Compose 一键部署。

## MVP 范围（用户已确认）

| 保留 | 明确不做（二期再说） |
|---|---|
| 注册 / 登录 / 登出 | 推荐算法（主页按最新倒序） |
| 主页双列瀑布流卡片 | 关注关系、点赞、评论、收藏 |
| 新建图文笔记（≤9 图） | 私信、管理后台、搜索 |
| 个人中心（资料编辑 + 我的笔记网格） | 地图 API、AI 安全门（接口留缝） |
| 笔记详情页（浏览闭环） | 视频上传（二期接 ffmpeg 管线） |

## 文档索引

| 文档 | 内容 | 给谁看/什么时候读 |
|---|---|---|
| **[AGENTS.md](AGENTS.md)** | **工作代理/协作者入口**：阅读顺序、施工守则、文档维护规约、状态快照 | 任何新会话第一步必读 |
| [docs/01-技术栈与总体架构.md](docs/01-技术栈与总体架构.md) | 技术选型理由、架构图、目录结构（分包风格对照）、核心设计决策（JWT 双令牌 / MQ 异步图片管线 / Feed 缓存 / 对象存储）、数据库 DDL 全量、"延后安装清单"回装成本评估 | 开工前通读，遇到"为什么这么设计"回来看 |
| [docs/02-API接口规范.md](docs/02-API接口规范.md) | 全部 MVP 接口定义：路径、鉴权、请求/响应示例、错误码表、状态机与时序图。前后端并行开发的契约文件 | 前后端各一份对照实现 |
| [docs/03-MVP实施与Docker部署.md](docs/03-MVP实施与Docker部署.md) | 从零到 `docker compose up` 的逐步施工手册：环境准备 → 脚手架 → 中间件 → 后端 8 步 → 前端 6 步 → 联调 → 容器化部署 → 冒烟验收 checklist | 施工时照着做 |
| [CHANGELOG.md](CHANGELOG.md) | 变更日志：一切产出物落盘/契约变更/决策变更的唯一记录处（追加制） | 每次变更后更新 |
| [docs/HANDOVER.md](docs/HANDOVER.md) | **持续维护的交接文档**：当前状态、怎么跑、验证证据、待办与坑位速查 | 新会话接管 / 每次阶段或验收变化后更新 |
| [docs/learning/00-学习笔记索引.md](docs/learning/00-学习笔记索引.md) | 学习笔记索引 + 12 个预定主题 backlog（踩坑与新概念强制沉淀） | 完成一个 Phase 或解掉一个坑后写一篇 |

## 技术栈一句话

Java 21 + Spring Boot 3.5 + MyBatis(+PageHelper/Flyway) + MySQL(InnoDB) + Redis + RabbitMQ + MinIO + Vue 3(Vite/Pinia/TailwindCSS) + Nginx + Docker Compose。

## 与思绪.txt 的演进路径

```
本 MVP（图文社区底盘）
 └─ 二期A：视频上传（ffmpeg in Docker）+ 地点标注（EXIF GPS 提取缝合点已在 MQ 管线中预留）
 └─ 二期B：AI 安全门（人物检测）：直接在 media.process 消费者链路上加一个 consumer
 └─ 三期：地图展示（Leaflet）、生物物种树（GBIF 接入），详见 ../docs/pixelfed/02-思绪Scenary改造可行性评估.md 的 P2-P3 规划
```

## 状态与变更记录

变更的唯一记录处是 [CHANGELOG.md](CHANGELOG.md)（追加制，当前 v2.16）；进度唯一真相源是 [docs/03](docs/03-MVP实施与Docker部署.md) 末尾的交付 Checklist；agent 会话入口固定为 [AGENTS.md](AGENTS.md)。三者分工不重复维护。
