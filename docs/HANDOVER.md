# Scenary · 交接文档（HANDOVER）

> 新会话/新协作者快速接管用。治理规则入口仍是 [../AGENTS.md](../AGENTS.md)，本文只回答"现状怎么跑、测什么、下一步做什么"。
> 更新时间：2026-09-02 · 对应版本 v2.16 · 进度真相源 [03 手册末尾 Checklist](03-MVP实施与Docker部署.md)

## 1. 一句话现状

后端 MVP 功能完备（18 端点全绿），前端六视图可用并已切换薄荷绿×青冥×茶白双主题；P4、P5 和 P6 均已验收，Phase 7 运维基线已落盘。全栈由 Docker Compose 在 `:8081` 对外提供，backend health、`/api`、`/minio` 和 SPA history 路由已实测。

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
| 上传管线 | `node docs/dev/test-media36.mjs` | 魔数欺骗/越权/删除闭环/对象可读 | ✅13 |
| MQ 闭环 | `node docs/dev/test-thumbnail37.mjs` | 出片尺寸语义/DLQ 双毒消息 | ✅7 |
| 端到端 | `node docs/dev/test-e2e38.mjs` | 发布/feed 缓存/可见性矩阵/软删幂等 | ✅29 |
| 前端门禁 | `cd frontend && npm run build` | SFC 静态校验 | ✅ |
| Phase 6 编排 | `docker compose config --quiet` | Compose 插值与 YAML | ✅ |
| Phase 6 全栈 | `docker compose up -d --build`、`docker compose ps` | backend health、服务依赖与 :8081 对外入口 | ✅ |
| Phase 6 冒烟 | 经 `http://localhost:8081/api/v1` 执行注册→上传→轮询→发布→匿名 feed | JWT 配置、MQ 缩略图、Nginx `/api` 与 `/minio`、SPA 路由 | ✅ |
| Phase 7 运维基线 | `pwsh -File ops/backup-mysql.ps1 -Preview`、`pwsh -File ops/watch-backend-errors.ps1 -Pattern ERROR`、`pwsh -File ops/check-dlq.ps1 -WarnAbove 0` | 备份/日志/DLQ 三件套（备份、DLQ 均经 Compose 容器执行） | ✅ |

脚本均已随机化用户名（v2.5 修复二次执行撞名）。IDEA HTTP 版冒烟：[smoke-backend.http](dev/smoke-backend.http)，图片夹具在 `docs/dev/fixtures/`（含真 webp）。

2026-09-02 已经由 Compose 对外入口 `http://localhost:8081/api/v1` 复验认证/拦截链/上传/端到端四组脚本，断言结果为 18/8/13/29。首页 L1 使用版本化键，发布或删除推进版本，避免并发旧查询回写到当前首页；仅默认 10 条首页走 L1，自定义 `limit` 保持独立查询语义。

## 4. 待办清单（按优先级）

> 当前工作边界：Phase 7 已完成；下一步转入二期A 预研，不在本轮展开。

1. **部署数据迁移评估**：开发期已有媒体行会持久化旧的 MinIO 直连 URL；切到仅 :8081 的全栈编排后，该历史内容需重传或写数据迁移改为 `/minio` 反代 URL。新上传内容已验证正常。
2. **二期A 预研**：视频上传/地点标注的接口缝已在架构中预留，但本轮不展开。

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
