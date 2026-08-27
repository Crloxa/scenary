# 03 · Compose 变量注入时机：${} 解析发生在宿主机而非容器内

> 触发：Phase 2 完成。亲测环境：Docker Compose v5.2 / Windows Docker Desktop（WSL2）。

## 现象

Phase 2 的 `docker-compose.middleware.yml` 里，MySQL 健康检查写的是：

```yaml
test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD}"]
```

直觉印象是"`${VAR}` 会引用容器内的环境变量"——毕竟同一文件里 `environment:` 块也是这么写的。但 `docker inspect` 看到的却是：

```json
["CMD","mysqladmin","ping","-h","localhost","-pscenary_root_2026"]
```

宿主机 `.env` 里的根密码已经变成**明文字符串**，烤进了容器元数据。

## 原因

Compose 文件里同一个 `${}` 写法有两种完全不同的解析路径：

| 出现位置 | 解析者 | 时机 | 结果 |
|---|---|---|---|
| yml 其它字段（command/healthcheck/ports…） | **Compose 自己**（项目目录 .env → shell env） | `docker compose up` 解析文件时（宿主机） | 替换为字面量后写入容器配置 |
| `environment:` 映射值 | 先由 Compose 同样做一轮宿主机插值；**未加 `${}` 的裸变量名才留给容器运行时取自身 env** | 混合 | 见下"易混点" |

也就是说：healthcheck 是 Compose 在解析阶段把 `.env` 的值拼进命令字符串，容器起来之后跟环境变量再无关系。

## 易混点记录

`environment:` 里两种写法语义不同：
- `FOO: ${BAR}` —— 宿主机插值，容器里 FOO 是死值；
- `FOO: $BAR` 之类不存在的形式并不转发容器变量；想让容器用自己进程环境的同名变量值应省略该键或用 `FOO: ${BAR:-}` 由 CLI 传入。
（本项目场景简单：全部走第一种，".env 为唯一真相源"，可接受。）

## 我怎么验证的

```bash
# 1) 启动中间件栈
docker compose -f docker-compose.middleware.yml up -d
# 2) 读容器配置中的健康检查原文
docker inspect --format '{{json .Config.Healthcheck.Test}}' scenary-mysql-1
#    → ["CMD","mysqladmin","ping","-h","localhost","-pscenary_root_2026"]   ✔ 明文可见
# 3) 反证 runtime 注入不受影响：容器内 env 无该插值残留差异
docker exec scenary-mysql-1 printenv MYSQL_ROOT_PASSWORD   # = .env 原值（environment 正常注入）
```

## 可复用结论

1. **`${}` 在 compose 文件的任何位置都是"宿主机解析期"行为**，别指望它在容器侧延迟求值。
2. 把敏感密码放进 command/healthcheck 等字段，等于让任何能跑 `docker inspect` 的人读到明文——单机开发可容忍（本手册如此设计），公网/多人共机部署时健康检查应改用免密探针（如 `mysqladmin ping` 配置专用低权账号、或 `--connect-timeout` 探测端口）并在文档中登记风险。
3. 排查"为什么容器里的命令不是我写的样子"时，第一动作永远是 `docker inspect .Config`，而不是猜。
