# Scenary 03 · MVP 实施与 Docker 部署手册

> 从空目录到 `docker compose up` 跑通全栈的分阶段施工文档。每个 Phase 有**产出物 + 验收标准**，做完打勾再前进。
> 设计依据：[01-技术栈与总体架构](01-技术栈与总体架构.md)；接口契约：[02-API接口规范](02-API接口规范.md)。
> 写作日期：2026-08-27 · 环境假设：Windows + Git Bash（命令均为 bash 可执行）
> 说明：仓库并存 `docker-compose.middleware.yml`（开发期中间件基座）与 `docker-compose.yml`（全栈编排），两份是有意分开的阶段性产物而非冗余，分别见 Phase 2 / Phase 6。

---

## 总览：七个 Phase 与预估工作量

| Phase | 内容 | 预估 |
|---|---|---|
| P0 | 环境准备 | 0.5 天 |
| P1 | 前后端脚手架落盘 | 0.5 天 |
| P2 | 中间件 compose 先行跑通 | 0.5 天 |
| P3 | 后端逐步实现（8 步） | 4~5 天 |
| P4 | 前端逐步实现（6 步） | 3~4 天 |
| P5 | 本地联调排障 | 1 天 |
| P6 | 容器化与一键部署 | 1 天 |
| P7 | 运维基线 | 0.5 天 |
| **合计** | | **约 11~13 天业余时间** |

---

## Phase 0 · 环境准备

安装并验证（命令通过即达标）：

```bash
docker --version          # Docker Desktop ≥ 4.30, WSL2 后端
docker compose version    # v2
java -version             # OpenJDK 21.x（本地开发用；部署构建在容器内进行）
mvn -version              # ≥ 3.9
node -v                   # ≥ 20 LTS（本地开发用）
git --version
```

注意事项：
- Docker Desktop → Settings → Resources：内存给到 **≥6GB**（mysql+redis+rabbitmq+minio+jdk+vite 同时跑）。
- 国内网络：为 Maven 配 aliyun mirror、npm 配 registry.npmmirror.com、Docker 配镜像加速器。
- 生成两把密钥备用（P6 会用）：

```bash
openssl rand -hex 48      # JWT_SECRET（hex 格式：无 +/= 字符，杜绝 shell/YAML 转义坑）
openssl rand -hex 16      # MinIO 密码（用户名固定 minio_admin）
```

---

## Phase 1 · 工程脚手架

### 1.1 目录骨架

```bash
cd /d/Resume_project/scenary
mkdir -p backend frontend docs   # docs 已存在则跳过
```

### 1.2 后端 Spring Boot 骨架

两种方式任选：
- A. start.spring.io 网页生成 zip 解压到 `backend/`；
- B. IDEA New Project → Spring Initializr。

依赖清单（Spring Boot 3.5.x / Java 21 / Maven）。脚手架不依赖 start.spring.io，由 `backend/pom.xml` 直接手工声明（2026-08-27 决策，CHANGELOG v1.4）：
`Spring Web`、`Spring Data Redis`、`Spring RabbitMQ`、`MyBatis Framework`、`MySQL Driver`、`Flyway Migration`、`Lombok`、`Validation`、`Spring Boot Actuator`。

再手工补进 pom 的依赖：

```xml
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId><version>0.12.6</version></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
<dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><version>0.12.6</version><scope>runtime</scope></dependency>
<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-crypto</artifactId></dependency>
<dependency><groupId>com.github.pagehelper</groupId><artifactId>pagehelper-spring-boot-starter</artifactId><version>2.1.0</version></dependency>
<dependency><groupId>io.minio</groupId><artifactId>minio</artifactId><version>8.5.17</version></dependency>
<dependency><groupId>net.coobird</groupId><artifactId>thumbnailator</artifactId><version>0.4.20</version></dependency>
```

按 [01 §4](01-技术栈与总体架构.md) 建好包目录（common/config/auth/user/media/note/feed/mq）。

### 1.3 前端 Vue 骨架

```bash
cd frontend
npm create vue@latest . -- --force     # 选: Router=Yes Pinia=Yes 其余 No(纯 JS)
npm install axios tailwindcss @tailwindcss/vite
```

vite.config.js 增加 dev 代理与 tailwind 插件：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } }
})
```

`src/assets/main.css` 首行 `@import "tailwindcss";`

**验收 P1**：`backend/` 下 `mvn -q compile` 通过；`frontend/` 下 `npm run dev` 打开默认欢迎页无报错。git init 并完成首次提交。

---

## Phase 2 · 中间件先行（开发基座）

创建仓库根目录两个文件：

**`.env.example`**（复制为 `.env` 使用）：

```ini
MYSQL_ROOT_PASSWORD=scenary_root_2026
MYSQL_DATABASE=scenary
MYSQL_USER=scenary
MYSQL_PASSWORD=scenary_pwd_2026
REDIS_ARGS=--appendonly no
RABBITMQ_DEFAULT_USER=scenary_mq
RABBITMQ_DEFAULT_PASS=mq_pwd_2026
MINIO_ROOT_USER=minio_admin
MINIO_ROOT_PASSWORD=minio_pwd_change_me_2026
MINIO_BUCKET=scenary-media
JWT_SECRET=请替换为Phase0生成的hex串
JWT_ACCESS_TTL_SECONDS=7200
JWT_REFRESH_TTL_SECONDS=2592000
PUBLIC_HOST=localhost        # 浏览器访问地址（冒烟脚本与图片反代用），服务器部署改为公网IP/域名
```

**`docker-compose.middleware.yml`**（开发期只起中间件）：

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      TZ: Asia/Shanghai
    command: [ "mysqld", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci" ]
    ports: ["3306:3306"]
    volumes: ["scenary-mysql-data:/var/lib/mysql"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    volumes: ["scenary-redis-data:/data"]

  rabbitmq:
    image: rabbitmq:3.13-management
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
    ports: ["5672:5672", "15672:15672"]
    volumes: ["scenary-mq-data:/var/lib/rabbitmq"]

  minio:
    image: minio/minio:latest
    command: ["server", "/data", "--console-address", ":9001"]
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    ports: ["9000:9000", "9001:9001"]
    volumes: ["scenary-minio-data:/data"]

  minio-init:                       # 一次性建桶容器
    image: minio/mc:latest
    depends_on: [minio]
    entrypoint: >
      /bin/sh -c "
      until mc alias set local http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}; do sleep 2; done;
      mc mb -p local/${MINIO_BUCKET} || true;
      mc anonymous set download local/${MINIO_BUCKET};
      echo BUCKET_READY"

volumes:
  scenary-mysql-data:
  scenary-redis-data:
  scenary-mq-data:
  scenary-minio-data:
```

启动与验收：

```bash
cp .env.example .env      # 改成你的真实值
docker compose -f docker-compose.middleware.yml up -d
docker compose -f docker-compose.middleware.yml ps   # 全部 healthy/running
# 验收四连:
docker exec -it $(docker ps -qf name=mysql) mysql -uscenary -p -e "SELECT VERSION();"   # 数据库连通
curl http://localhost:6379 2>&1 | head -1        # 有 RESP 响应即可(-ERR 正常)
浏览器打开 http://localhost:15672                  # RabbitMQ 管理台登录
浏览器打开 http://localhost:9001                   # MinIO 控制台,应看到 scenary-media 桶且策略为download
```

---

## Phase 3 · 后端逐步实现（建议严格按序，每步可独立验证）

统一验收方式：`mvn spring-boot:run -Dspring-boot.run.profiles=dev` 启动后用 curl 敲对应端点。

**application-dev.yml 关键配置**（步骤 0 与脚手架一起落地）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/scenary?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai
    username: scenary
    password: scenary_pwd_2026
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis: { host: localhost, port: 6379 }
  rabbitmq:
    host: localhost
    username: scenary_mq
    password: mq_pwd_2026
    listener:
      simple: { acknowledge-mode: manual, concurrency: 2, prefetch: 1,
                default-requeue-rejected: false }
  servlet:
    multipart: { max-file-size: 10MB, max-request-size: 100MB }
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration: { map-underscore-to-camel-case: true }
scenary:
  jwt: { secret: ${JWT_SECRET}, access-ttl: 7200, refresh-ttl: 2592000, issuer: scenary }
  minio:
    endpoint: http://localhost:9000
    access-key: minio_admin
    secret-key: minio_pwd_change_me_2026
    bucket: scenary-media
    public-host: http://localhost:9000   # 生成外链用的host
```

### 步骤清单

| # | 任务 | 涉及新文件 | 自测命令 |
|---|---|---|---|
| 3.1 | Flyway V1__init.sql（粘贴 [01 §6](01-技术栈与总体架构.md) DDL）+ 连库冒烟 | `db/migration/V1__init.sql` | 启动日志出现 `Successfully applied 1 migration` |
| 3.2 | common 包：Result/ErrorCode/BizException/GlobalExceptionHandler/PageResult；PingController | `common/*` | `curl localhost:8080/api/v1/ping` → `{"code":0,...}` |
| 3.3 | UserEntity/UserMapper(+XML)/users 表 CRUD；BCrypt Bean | `user/*` | 单测或 tinker 不强求，由 3.4 覆盖 |
| 3.4 | 认证全套：JwtProperties/JwtUtil(sign&verify)、AuthService(register/login/refresh/logout, Redis 白名单+限流计数器)、AuthController | `auth/*` | register→login→refresh→logout 四连 curl 全绿（对照 02 文档示例） |
| 3.5 | AuthInterceptor + WebConfig 注册拦截路径 `/users/me/**`,`/media/**`(除 GET feed),`/notes(POST,DELETE)`;UserContext ThreadLocal | `config/WebConfig.java`,`auth/AuthInterceptor.java` | 无 token 访问 PATCH /users/me → 40100；带 token → 40400(因 me 未实现可先返假数据) |
| 3.6 | MinioService(putObject/getObject/remove)+MinioConfig；MediaService.uploadImages(校验mime魔数→存原图→insert media→发 MQ)+MediaController | `config/MinioConfig.java`,`media/*` | curl -F 上传两张 → media 表新增 status=0 行；MinIO 控制台见 orig/ 对象 |
| 3.7 | RabbitConfig(topic exchange `media.event` + queue `media.thumbnail.q` 绑定 routingKey `media.uploaded` + DLX/DLQ)；ThumbnailConsumer（Thumbnailator ≤800px q0.8 → 存 thumb → update status/thumbUrl/w/h → ack；异常两次重试后 nack 进 DLQ） | `config/RabbitConfig.java`,`mq/ThumbnailConsumer.java` | 上传后 2 秒轮询 GET /media/{id} 变 status=1 且 thumbUrl 可在浏览器打开；制造坏消息确认落入 media.dlq |
| 3.8 | Note(note insert/delete/详情聚合) + Feed(cursor 分页 SQL + 两级缓存 + 发布删缓存) + UserController(me/profile/notes/avatar 同步缩放) | `note/*`,`feed/*`,`user/UserController.java` | 按 02 文档把全部 18 个端点 curl 过一遍，核对响应 JSON 结构一致 |

**验收 P3**：写一份 `docs/dev/smoke-backend.http`（IDEA HTTP Client 格式或 curl 脚本），覆盖 02 文档所有接口正反例，全部符合预期错误码。此时**后端已功能完备**，前端可以完全并行。

---

## Phase 4 · 前端逐步实现

| # | 任务 | 要点 |
|---|---|---|
| 4.1 | 清理模板 + 视觉基底 | 删掉 HelloWorld 等 demo；main.css 引入 tailwind + 自定义主题色变量（低饱和"高雅"色板：主色 `#c2410c` 类陶土橙 或 莫兰迪绿系任选其一）；全局字体 system-ui |
| 4.2 | 基础设施层 | `utils/request.js`(axios 实例/baseURL=/api/v1/请求头注入/401刷新重放队列防并发重复刷新)；`stores/user.js`(token 持久化 localStorage);`router/index.js` 全路由表+守卫;`api/*.js` 六个模块对应 02 文档逐一封装 |
| 4.3 | LoginView | 登录/注册双 Tab 表单；客户端校验规则与 02 一致；成功后 pinia 存态+跳 redirect |
| 4.4 | TopNav + HomeView | TopNav: logo+发布按钮(笔形 icon)+头像下拉(me/退出)；HomeView: 双列 CSS columns 瀑布流，NoteCard 组件(封面图 aspect-ratio 占位/loading=lazy/标题/contentPreview/作者行)，IntersectionObserver 哨兵触发 loadMore(nextCursor)，首屏骨架屏 8 卡 |
| 4.5 | PublishView | 文件选择(≤9张即时本地预览可删排序)→并发上传+每张状态徽标(处理中转圈/失败红标允许移除重传)→表单(标题计数器/正文/placeName 选填)→提交成功跳首页;未登录访问走守卫 |
| 4.6 | ProfileView + NoteDetailView | Profile: 用户信息卡(头像昵称bio计数)+九宫格笔记(id==me 时加编辑资料弹窗、退出登录)；他人访问只读。NoteDetail: 大图竖向流(可左右切换或纵向排列)+作者卡+标题正文;私密他人访问展示 404 态 |

**验收 P4**：无后端报错前提下手动走完剧本——注册 A → 发 3 篇笔记（含混 1 张 webp）→ 登出 → 注册 B 浏览瀑布流点开卡片刷新不丢路由 → B 尝试删 A 的笔记（UI 无入口即达标）→ A 删除自己的某篇确认从列表消失。控制台无红错，强刷页面登录态保持。

---

## Phase 5 · 本地联调排障速查

| 症状 | 大概率原因 | 处置 |
|---|---|---|
| 后端起不来 Flyway 报错 | middleware 未起/账号密码不符 | 先 `docker compose -f docker-compose.middleware.yml ps`，核对 .env 与 dev.yml 一致 |
| 上传 403 MinIO SignatureDoesNotMatch | 密钥两侧不一致 | 对照 .env 和 application-dev.yml |
| MQ 消息堆积没人消费 | consumer 异常反复 nack | 看 RabbitMQ 控制台 media.dlq 深度 + backend 日志 ThumbnailConsumer 段 |
| 图片 404 | public-host 配置与浏览器访问 host 不符(如容器内地址) | public-host 必须是浏览器可达地址 |
| 时差 8 小时 | JDBC/JVM 时区缺失 | 连接串 connectionTimeZone=Asia/Shanghai + JVM 参数(见 01 §8) |
| vite 代理 502 | 后端没起来或端口占用 | curl localhost:8080/api/v1/ping 直接验证 |

---

## Phase 6 · 容器化与一键部署

### 6.1 backend/Dockerfile（多阶段）

```dockerfile
# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app/target/scenary-backend-*.jar app.jar
ENV TZ=Asia/Shanghai JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

### 6.2 frontend/Dockerfile

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

### 6.3 frontend/nginx.conf

```nginx
server {
  listen 80;
  server_name _;
  client_max_body_size 100m;

  gzip on;
  gzip_types text/css application/javascript application/json image/svg+xml;

  location /api/ {
    proxy_pass http://backend:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_read_timeout 120s;
  }

  location /minio/ {                  # 对象存储同源反代(方案B, 见6.4说明)
    proxy_pass http://minio:9000/;
    proxy_set_header Host $host;
  }

  location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;   # SPA history 路由兜底
  }

  location ~* \.(js|css|png|jpg|jpeg|webp|gif|svg|woff2?)$ {
    root /usr/share/nginx/html;
    expires 30d;
    add_header Cache-Control "public";
  }
}
```

### 6.4 根目录 docker-compose.yml（全栈生产式）

```yaml
services:
  mysql:
    image: mysql:8.4
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      TZ: Asia/Shanghai
    command: [ "mysqld", "--character-set-server=utf8mb4",
               "--collation-server=utf8mb4_unicode_ci" ]
    volumes: ["scenary-mysql-data:/var/lib/mysql"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 15

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: sh -c "${REDIS_ARGS}"
    volumes: ["scenary-redis-data:/data"]

  rabbitmq:
    image: rabbitmq:3.13-management
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
    volumes: ["scenary-mq-data:/var/lib/rabbitmq"]

  minio:
    image: minio/minio:latest
    restart: unless-stopped
    command: ["server", "/data"]
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes: ["scenary-minio-data:/data"]

  minio-init:
    image: minio/mc:latest
    depends_on: [minio]
    entrypoint: >
      /bin/sh -c "
      until mc alias set local http://minio:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}; do sleep 2; done;
      mc mb -p local/${MINIO_BUCKET} || true;
      mc anonymous set download local/${MINIO_BUCKET};
      echo BUCKET_READY"

  backend:
    build: ./backend
    restart: unless-stopped
    depends_on:
      mysql: { condition: service_healthy }
      rabbitmq: { condition: service_started }
      minio: { condition: service_started }
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_DEFAULT_USER}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_DEFAULT_PASS}
      SCENARY_MINIO_ENDPOINT: http://minio:9000
      SCENARY_MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      SCENARY_MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      SCENARY_MINIO_BUCKET: ${MINIO_BUCKET}
      SCENARY_MINIO_PUBLIC_HOST: http://${PUBLIC_HOST}:8081/minio   # 见下方“反代说明”
      JWT_SECRET: ${JWT_SECRET}
      TZ: Asia/Shanghai
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]
      interval: 15s
      timeout: 5s
      retries: 10

  frontend:
    build: ./frontend
    restart: unless-stopped
    depends_on:
      backend: { condition: service_healthy }
    ports: ["8081:80"]           # 唯一对外端口

volumes:
  scenary-mysql-data:
  scenary-redis-data:
  scenary-mq-data:
  scenary-minio-data:
```

> **对象 URL 反代说明（重要）**：compose 内网里 backend 存取用 `http://minio:9000`，但**浏览器**要能打开接口返回的图片链接，靠的是 public-host 配置。两种方案（默认 B，6.3 的 nginx.conf 已内置 `/minio/` 反代段）：
> - **B（整洁，推荐）**：浏览器经同一端口走反代——`SCENARY_MINIO_PUBLIC_HOST=http://${PUBLIC_HOST}:8081/minio`；
> - **A（直连）**：给 minio 服务加 `ports: ["9000:9000"]` 映射，public-host 直接设 `http://${PUBLIC_HOST}:9000`。
>
> 两方案只需保证该变量与 nginx 配置匹配；前端代码零改动（它只认接口返回的完整 URL）。
>
> **为什么没有 application-prod.yml？** 上述 compose 的 `SPRING_DATASOURCE_URL / SPRING_DATA_REDIS_HOST / SPRING_RABBITMQ_* / SCENARY_MINIO_* / JWT_SECRET` 等标准命名环境变量，经 Spring Boot ** relaxed binding** 直接覆盖 yml 同名配置项，优先级高于任何 profile 文件，因此无需为容器单独维护一份 prod 配置；本地开发仍用 application-dev.yml。

### 6.5 构建与启动命令序列

```bash
cd /d/Resume_project/scenary
cp .env.example .env && nano .env            # 填真实密钥/PUBLIC_HOST=localhost
docker compose -f docker-compose.middleware.yml down   # 若开发栈在跑,释放卷冲突(卷同名共享可跳过)
docker compose build                          # 首次构建 5~15 分钟视网络
docker compose up -d
docker compose ps                             # 六个服务 healthy/running
docker compose logs -f backend                # 看到 Flyway 成功 + Tomcat started
```

### 6.6 冒烟测试剧本（全绿=MVP 交付）

```bash
BASE=http://localhost:8081/api/v1
# 1.健康
curl -s $BASE/ping | grep '"code":0'
# 2.注册A, 取 accessToken(JQ 或手工复制)
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"smoke_a","password":"Passw0rd123"}'
# 3.上传图(任意 jpg)
TOKEN=<上一步accessToken>; curl -s -X POST $BASE/media/images \
  -H "Authorization: Bearer $TOKEN" -F "files=@test.jpg"
# 4.等2秒查状态→status=1 ; 5.发笔记 mediaIds=[上一步]; 6.匿名 GET $BASE/feed 应看到卡片
# 7.浏览器打开 http://localhost:8081 走一遍 UI 剧本(P4 验收剧本)
```

（第 3 步的 `test.jpg` 用任意本地图片即可：≤10MB、jpeg/png/webp 格式。）

---

## Phase 7 · 运维基线

- **备份**：`docker compose exec mysql sh -c 'mysqldump -uroot -p$MYSQL_ROOT_PASSWORD scenary' > backup_$(date +%F).sql`，每周 cron；MinIO 卷同盘冷备可选 `mc mirror`。
- **升级**：改代码 → `docker compose build backend frontend` → `docker compose up -d backend frontend`（中间件与数据卷不动；DDL 一律走新的 Flyway `Vn__xxx.sql`）。
- **日志**：`docker compose logs -f backend | grep ERROR`；消费者问题看 RabbitMQ 管理台队列深度与 DLQ。
- **安全底线**：`.env` 不进 git；公网部署时给 nginx 加 HTTPS(certbot)并把 15672/9001 等管理端口撤下对外映射。

---

## 附 · MVP 交付 Checklist

<details open>
<summary>展开勾选</summary>

- [x] P0 四件套工具版本验证通过（2026-08-27：docker 29.6.1/compose v5.2.0/JDK 21.0.11/mvn 3.9.11/node 22.23.2/git 2.46；本机默认 JDK 已由 17 切换为已有 ms-21.0.11，过程见 learning/01）
- [x] P1 后端编译通过 / 前端 dev 页面可用 / git 首提交（2026-08-27：`mvn -q compile` EXIT=0；Vite 8 dev HTTP 200 无报错；main 分支三笔原子提交。脚手架为手写 pom，未用 start.spring.io，见 CHANGELOG v1.4）
- [x] P2 四中间件 healthy + MinIO 桶自动建立（2026-08-27：mysql 8.4.11 healthy+业务账号连通 / redis +PONG / rabbitmq 3.13.7 管理 API / minio 健康 200 + scenary-media 桶 download 权限就绪）
- [x] 3.1 Flyway 三表就绪（2026-08-27：Successfully applied 1 migration；users/notes/media + flyway_schema_history 实查通过）
- [x] 3.2 ping 包络正常（2026-08-27：`{"code":0,"message":"ok","data":{"pong":"v1"}}` 与契约逐字段一致）
- [x] 3.4 注册/登录/刷新/登出四接口全绿（2026-08-27：18 用例脚本 docs/dev/test-auth34.mjs 全 PASS，含旋转重放拒绝/登出吊销/登录锁三组安全用例）
- [x] 3.5 无 token 拦截生效 / ThreadLocal 清理无泄漏（2026-08-27：8 用例脚本 docs/dev/test-interceptor35.mjs 全 PASS，含非 Bearer 方案/垃圾 JWT/refresh 当 access/登出黑名单/连续请求无串号）
- [x] 3.6 上传入 MinIO + media 行落库 + MQ 消息发出（2026-08-27：13 用例全 PASS；DB 实查 4 行 status=0；mc 列出 orig/202608/ 全部对象且匿名可读；MQ 以临时队列实证 mediaId 消息入队——管理插件 publish 计数器在该版本恒 0 属统计怪癖，已以队列深度替代证据；附带含契约形状 data.items 修正）
- [x] 3.7 缩略图消费成功 + DLQ 兜底验证（2026-08-27：7 用例全 PASS——800ms 内出片/限边等比 800x483 与不放大 300x200 双证/thumb 匿名可读 7.6KB jpeg/两枚毒消息重试两跳后落 DLQ 深度 0→2/毒消息后正常流不受阻；日志与 thumb 对象清单随 CHANGELOG v2.2 存档）
- [x] 3.8 笔记发布/详情/删除、feed 游标翻页、个人中心全绿（2026-08-27：test-e2e38.mjs 29 用例全 PASS，覆盖资料/头像/发布/feed 两级缓存与翻页/mine 判定/私密可见性(契约 v2.3 补 visibility 字段)/软删幂等/越权矩阵；过程揪出 INSERT 漏列 visibility 缺陷并修复）
- [x] 3.9 smoke-backend.http 存档（IDEA HTTP Client 格式全端点正反例 + 可编程 mjs 四件套：auth34/interceptor35/media36/thumbnail37/e2e38）
- [ ] 4.1-4.6 六个前端任务各自验收通过
- [ ] P5 联调无阻塞、已知坑记录回填本表
- [ ] 6.5 compose 全栈一次拉起成功
- [ ] 6.6 冒烟剧本 7 步全过
- [ ] README 状态表更新为"MVP 已部署"

</details>
