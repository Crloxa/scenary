# Scenary · 交接文档（HANDOVER）

> 新会话/新协作者快速接管用。治理规则入口仍是 [../AGENTS.md](../AGENTS.md)，本文只回答"现状怎么跑、测什么、下一步做什么"。
> 更新时间：2026-08-29 · 对应版本 v2.7 · 进度真相源 [03 手册末尾 Checklist](03-MVP实施与Docker部署.md)

## 1. 一句话现状

后端 MVP 功能完备（18 端点全绿），前端六视图可用并已切换薄荷绿×青冥×茶白双主题，P4 浏览器验收剧本已完成；当前停在 Phase 4 完成后的 checkpoint，Phase 5~7（联调排障/容器化/运维基线）未开始。

## 2. 如何跑起来

```bash
# 前提：JDK21（run-dev.sh 自动守卫）、Docker Desktop 运行中
cd /d/Resume_project/scenary
docker compose -f docker-compose.middleware.yml up -d     # 中间件四件套
cd backend && bash run-dev.sh &                           # 后端 :8080（自动注入 .env 密钥）
cd frontend && npm run dev                                # 前端 :5173（/api 代理到 :8080）
```

浏览器打开 http://localhost:5173 。`.env` 已存在且不入库；丢失密钥按 Phase 0 重生成并同步改 `.env` 的 JWT_SECRET/MINIO_ROOT_PASSWORD 即可。

## 3. 测试矩阵（全部可重复执行）

| 层 | 命令 | 覆盖 | 当前状态 |
|---|---|---|---|
| 认证安全 | `node docs/dev/test-auth34.mjs` | 注册/旋转重放/登出吊销/登录锁 | ✅18 |
| 拦截链 | `node docs/dev/test-interceptor35.mjs` | 无令牌矩阵/黑名单/ThreadLocal 清理 | ✅8 |
| 上传管线 | `node docs/dev/test-media36.mjs` | 魔数欺骗/越权/删除闭环/对象可读 | ✅13 |
| MQ 闭环 | `node docs/dev/test-thumbnail37.mjs` | 出片尺寸语义/DLQ 双毒消息 | ✅7 |
| 端到端 | `node docs/dev/test-e2e38.mjs` | 发布/feed 缓存/可见性矩阵/软删幂等 | ✅29 |
| 前端门禁 | `cd frontend && npm run build` | SFC 静态校验 | ✅ |

脚本均已随机化用户名（v2.5 修复二次执行撞名）。IDEA HTTP 版冒烟：[smoke-backend.http](dev/smoke-backend.http)，图片夹具在 `docs/dev/fixtures/`（含真 webp）。

## 4. 待办清单（按优先级）

> 当前工作边界：用户已明确暂不开始 Phase 5；本文件只维护现状和待办，不代表 Phase 5 已启动。

1. **Phase 5 联调排障**：按手册速查表走一遍异常路径；真实多图上传性能观察。
2. **Phase 6 容器化**：两个 Dockerfile/nginx.conf/compose 全栈编排照手册抄录落地；注意 `.env` 的 PUBLIC_HOST。
3. **Phase 7 运维基线**：备份 cron、日志 grep 约定、DLQ 深度告警（学习笔记 07 的 TODO）。

## 5. 关键决策与坑位速查（细节见对应文档）

| 主题 | 出处 |
|---|---|
| Boot 基线为何是 3.5.16 且不依赖 start.spring.io | CHANGELOG v1.3/v1.4 |
| 后端运行需 run-dev.sh 注入 env + JDK 守卫原因 | learning/01、CHANGELOG v1.7 |
| healthcheck 内插密码的明文风险 | learning/03 |
| MQ 计数器恒 0 怪癖 + 发布早于绑定黑洞 | learning/07 |
| feed 两级缓存失效策略（DEL feed:first:v1 / note:card:{id}） | docs/01 §5.3 |
| visibility 契约补丁（POST /notes 可选字段） | CHANGELOG v2.3、docs/02 §5.1 |
| 前端双主题实现（token 化 + html.dark + FOUC 预判脚本） | learning/09、assets/main.css |

## 6. 服务账号速记

MySQL 业务账号 scenary / scenary_pwd_2026；RabbitMQ 管理（:15672）scenary_mq / mq_pwd_2026；MinIO 控制台（:9001）minio_admin /（hex16，见 .env）。全部仅限本机开发环境。
