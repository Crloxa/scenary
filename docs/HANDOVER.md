# Scenary · 交接文档（HANDOVER）

> 新会话/新协作者快速接管用。治理规则入口仍是 [../AGENTS.md](../AGENTS.md)，本文只回答"现状怎么跑、测什么、下一步做什么"。
> 更新时间：2026-09-04 · 对应版本 v2.29 · 进度真相源 [03 手册末尾 Checklist](03-MVP实施与Docker部署.md) · 最新证据 [P10 评论与通知验收](evidence/2026-09-04-P10评论与通知验收.md) · 当前改进入口 [05 后续开发路线图](05-后续开发路线图与实施手册.md) · 最新学习笔记 [16 P10 评论通知事务与软删除](learning/16-P10评论通知事务与软删除.md)

## 1. 一句话现状

后端 MVP 主链路已验收（18 端点全绿），前端七视图可用并已切换薄荷绿×青冥×茶白双主题；P4、P5、P6 和 Phase 7 均已验收。P8 已于 2026-09-03 通过全量出口门禁；P9 已于 2026-09-04 完成社交最小闭环；P10 已于 2026-09-04 完成评论与通知闭环。全栈由 Docker Compose 在 `:8081` 对外提供，backend health、`/api`、`/minio` 和 SPA history 路由已实测。

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
| P10 评论通知黑盒 | `node docs/dev/test-p10-comments.mjs` | V5、评论/回复、限流、软删占位、通知、未读/已读和可见性边界 | ✅17 |
| 后端单元 | `cd backend && mvn test -q` | 认证/媒体/消费者/traceId/Feed/笔记/社交/评论/通知边界回归 | ✅32 |
| 前端单元 | `cd frontend && npm test` | request、媒体、发布、登录、NoteCard、社交、评论/通知 API 与视图 | ✅21 |
| 前端浏览器 | `cd frontend && npm run test:e2e` | 390px 路由、登录表单键盘操作、console/page error | ✅1 |
| 前端门禁 | `cd frontend && npm run build` | SFC 静态校验 | ✅104 modules |
| Phase 6 编排 | `docker compose config --quiet` | Compose 插值与 YAML | ✅ |
| Phase 6 全栈 | `docker compose up -d --build`、`docker compose ps` | backend health、服务依赖与 :8081 对外入口 | ✅ |
| Phase 6 冒烟 | 经 `http://localhost:8081/api/v1` 执行注册→上传→轮询→发布→匿名 feed | JWT 配置、MQ 缩略图、Nginx `/api` 与 `/minio`、SPA 路由 | ✅ |
| Phase 7 运维基线 | `pwsh -File ops/backup-mysql.ps1 -Preview`、`pwsh -File ops/watch-backend-errors.ps1 -Pattern ERROR`、`pwsh -File ops/check-dlq.ps1 -WarnAbove 0` | 备份/日志/DLQ 三件套（备份、DLQ 均经 Compose 容器执行） | ✅ |

脚本均已随机化用户名（v2.5 修复二次执行撞名）。IDEA HTTP 版冒烟：[smoke-backend.http](dev/smoke-backend.http)，图片夹具在 `docs/dev/fixtures/`（含真 webp）。

2026-09-03 P8 最终 Compose 复验由入口 `http://localhost:8081/api/v1` 得到认证/拦截链/上传/缩略图/端到端五组 `18/8/15/7/30`，共 `78/78`；P8 Compose 集成矩阵 `6/6`，合计 `84/84`。2026-09-04 P9 黑盒 `13/13`、后端 Docker Maven JUnit `26/26`、前端 Vitest `15/15`、Vite 构建 `101 modules`；P9 浏览器只读主流程确认首页卡片和详情页提供明确的点赞/收藏控件，`/bookmarks` 路由可达。2026-09-04 P10 黑盒 `17/17`、主机 Maven JUnit `32/32`、前端 Vitest `21/21`、Vite 构建 `104 modules`、Playwright `1/1`；V5 已应用，Compose 重建/健康和浏览器只读评论/通知路由均通过。

P8 完整证据、环境指纹与验证边界归档在 [2026-09-03-P8改进验收](evidence/2026-09-03-P8改进验收.md)；P9 证据归档在 [2026-09-04-P9社交最小闭环验收](evidence/2026-09-04-P9社交最小闭环验收.md)；P10 证据归档在 [2026-09-04-P10评论与通知验收](evidence/2026-09-04-P10评论与通知验收.md)；历史 MVP 基线见 [2026-09-02-MVP复验报告](evidence/2026-09-02-MVP复验.md)。后续 Phase/功能环节仍必须先按 [证据链规范](evidence/README.md) 落盘报告；下一步进入 P11 搜索与发现。

## 4. 待办清单（按优先级）

> 当前工作边界：Phase 7 已完成，P8、P9、P10 均已通过全量出口验收；下一步按 05 手册进入 P11 搜索与发现。

1. **历史媒体 URL 迁移**：开发期已有媒体行可能持久化旧 MinIO 直连 URL；已提供 `ops/migrate-media-urls.ps1`，先用 `-Preview` 核对参数，再按新旧 public host 批量重建 media、note 封面和可选头像 URL。实际历史数据迁移仍是部署前运维动作。
2. **P8 证据维护**：若正式执行历史 URL 迁移，需追加运维证据和 CHANGELOG 勘误，不回改本报告历史结果。
3. **后续路线**：P10 已完成并归档证据；下一步为 P11 搜索与发现，视频/地点进入 P12。

> 当前施工记录：P8-01~P8-22、P9-01~P9-05 与 P10-01~P10-06 均已落地并完成验证；P10 出口报告为 `docs/evidence/2026-09-04-P10评论与通知验收.md`，学习笔记为 `docs/learning/16-P10评论通知事务与软删除.md`；03 Checklist 已勾选 P8、P9、P10。

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

## 6. 配置与安全速记

服务账号、密码和 JWT 密钥仅存于本机 `.env`，不写入交接文档、日志或提交记录。全栈编排只暴露 `:8081`；中间件管理端口仅由开发期 `docker-compose.middleware.yml` 映射。
