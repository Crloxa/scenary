# Scenary 03 · MVP 实施与 Docker 部署手册

> 从空目录到 `docker compose up` 跑通全栈的分阶段施工文档。每个 Phase 有**产出物 + 验收标准**，做完打勾再前进。
> 设计依据：[01-技术栈与总体架构](01-技术栈与总体架构.md)；接口契约：[02-API接口规范](02-API接口规范.md)。
> 写作日期：2026-08-27 · 环境假设：Windows + Git Bash（命令均为 bash 可执行）
> 说明：仓库并存 `docker-compose.middleware.yml`（开发期中间件基座）与 `docker-compose.yml`（全栈编排），两份是有意分开的阶段性产物而非冗余，分别见 Phase 2 / Phase 6。
> 证据门禁：从二期 A 起，每个 Phase/功能环节除通过本节验收并勾选 Checklist 外，还必须按 [evidence/README.md](evidence/README.md) 提交可复现报告；报告缺失或必需命令无法重跑时不得进入完成态。MVP 基线复验见 [2026-09-02-MVP复验.md](evidence/2026-09-02-MVP复验.md)。
> 改进施工：MVP 复验后发现的产品与工程缺陷统一进入 [04-产品与工程改进总纲](04-产品与工程改进总纲.md) 的 P8，不回写为“已完成 MVP”或在本手册中零散插入补丁；后续能力路线见 [05-后续开发路线图与实施手册](05-后续开发路线图与实施手册.md)。

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
    image: mysql:8.4@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb
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
    image: redis:7-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf
    ports: ["6379:6379"]
    volumes: ["scenary-redis-data:/data"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  rabbitmq:
    image: rabbitmq:3.13-management@sha256:e582c0bc7766f3342496d8485efb5a1df782b5ce3886ad017e2eaae442311f69
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
    ports: ["5672:5672", "15672:15672"]
    volumes: ["scenary-mq-data:/var/lib/rabbitmq"]

  minio:
    image: minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1@sha256:13582eff79c6605a2d315bdd0e70164142ea7e98fc8411e9e10d089502a6d883
    command: ["server", "/data", "--console-address", ":9001"]
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    ports: ["9000:9000", "9001:9001"]
    volumes: ["scenary-minio-data:/data"]

  minio-init:                       # 一次性建桶容器
    image: minio/mc:RELEASE.2025-08-13T08-35-41Z-cpuv1@sha256:95b5b3f7969a5c5a9f3a700ba72d5c84172819e13385aaf916e237cf111ab868
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
    multipart: { max-file-size: 200MB, max-request-size: 205MB }
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
FROM maven:3.9-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S app \
    && adduser -S app -G app
USER app
COPY --from=build /app/target/scenary-backend-*.jar app.jar
ENV TZ=Asia/Shanghai JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
```

### 6.2 frontend/Dockerfile

```dockerfile
FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build
WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10
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

  location ^~ /minio/ {               # 先于下方静态资源正则匹配，确保图片走对象存储反代
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
    image: mysql:8.4@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb
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
    image: redis:7-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf
    restart: unless-stopped
    command: ["sh", "-c", "exec redis-server ${REDIS_ARGS}"]
    volumes: ["scenary-redis-data:/data"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  rabbitmq:
    image: rabbitmq:3.13-management@sha256:e582c0bc7766f3342496d8485efb5a1df782b5ce3886ad017e2eaae442311f69
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
    volumes: ["scenary-mq-data:/var/lib/rabbitmq"]

  minio:
    image: minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1@sha256:13582eff79c6605a2d315bdd0e70164142ea7e98fc8411e9e10d089502a6d883
    restart: unless-stopped
    command: ["server", "/data"]
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes: ["scenary-minio-data:/data"]

  minio-init:
    image: minio/mc:RELEASE.2025-08-13T08-35-41Z-cpuv1@sha256:95b5b3f7969a5c5a9f3a700ba72d5c84172819e13385aaf916e237cf111ab868
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
      redis: { condition: service_healthy }
      rabbitmq: { condition: service_started }
      minio-init: { condition: service_completed_successfully }
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_DEFAULT_USER}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_DEFAULT_PASS}
      SPRING_RABBITMQ_LISTENER_SIMPLE_ACKNOWLEDGE_MODE: manual
      SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY: 2
      SPRING_RABBITMQ_LISTENER_SIMPLE_PREFETCH: 1
      SPRING_RABBITMQ_LISTENER_SIMPLE_DEFAULT_REQUEUE_REJECTED: "false"
      SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: 200MB
      SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: 205MB
      MYBATIS_MAPPER_LOCATIONS: classpath:mapper/*.xml
      MYBATIS_CONFIGURATION_MAP_UNDERSCORE_TO_CAMEL_CASE: "true"
      SCENARY_MINIO_ENDPOINT: http://minio:9000
      SCENARY_MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      SCENARY_MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      SCENARY_MINIO_BUCKET: ${MINIO_BUCKET}
      SCENARY_MINIO_PUBLIC_HOST: http://${PUBLIC_HOST}:8081/minio   # 见下方“反代说明”
      SCENARY_MINIO_EXPOSE_ORIGINAL_URL: "false"
      SCENARY_JWT_SECRET: ${JWT_SECRET}
      SCENARY_JWT_ACCESS_TTL: ${JWT_ACCESS_TTL_SECONDS}
      SCENARY_JWT_REFRESH_TTL: ${JWT_REFRESH_TTL_SECONDS}
      SCENARY_JWT_ISSUER: scenary
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
> 另外，`SCENARY_MINIO_EXPOSE_ORIGINAL_URL=false` 是默认策略：接口优先返回缩略图展示地址，只有显式打开时才把原图直接暴露给客户端。
>
> 两方案只需保证该变量与 nginx 配置匹配；前端代码零改动（它只认接口返回的完整 URL）。
>
> **为什么没有 application-prod.yml？** 上述 compose 的 `SPRING_DATASOURCE_URL / SPRING_DATA_REDIS_HOST / SPRING_RABBITMQ_* / SPRING_SERVLET_MULTIPART_* / MYBATIS_* / SCENARY_MINIO_* / SCENARY_JWT_*` 等环境变量，经 Spring Boot **relaxed binding** 直接映射到配置前缀（例如 `SCENARY_JWT_SECRET` → `scenary.jwt.secret`），优先级高于任何 profile 文件，因此无需为容器单独维护一份 prod 配置；本地开发仍用 application-dev.yml。Redis 参数不能直接交给 `sh`，必须由 `redis-server` 接收。

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

- **备份**：`ops/backup-mysql.ps1` 通过 Compose 内的 MySQL 容器生成 `backups/backup_YYYY-MM-DD_HHMMSS.sql`；先用 `-Preview` 确认命令，Linux cron/Windows 任务计划程序都可直接调用。MinIO 卷同盘冷备可选 `mc mirror`。
- **升级**：改代码 → `docker compose build backend frontend` → `docker compose up -d backend frontend`（中间件与数据卷不动；DDL 一律走新的 Flyway `Vn__xxx.sql`）。
- **日志**：`ops/watch-backend-errors.ps1 -Follow` 统一看 backend ERROR；不需要跟随时去掉 `-Follow`。消费者问题继续看 RabbitMQ 管理台队列深度与 DLQ。
- **DLQ 告警**：`ops/check-dlq.ps1` 通过 Compose 内的 `rabbitmqctl` 检查 `media.dlq` 深度，不依赖对外暴露管理端口或环境变量；超阈值返回 `exit 2`，适合 cron/任务计划程序接入。
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
- [x] 3.8 笔记发布/详情/删除、feed 游标翻页、个人中心全绿（2026-08-27：test-e2e38.mjs 29 用例全 PASS，覆盖资料/头像/发布/feed 两级缓存与翻页/mine 判定/私密可见性(契约 v2.3 补 visibility 字段)/软删幂等/越权矩阵；过程揪出 INSERT 漏列 visibility 缺陷并修复；2026-09-02 经 :8081 复验 29/29，并修复 L1 反序列化、分页参数复用和并发旧页回填）
- [x] 3.9 smoke-backend.http 存档（IDEA HTTP Client 格式全端点正反例 + 可编程 mjs 四件套：auth34/interceptor35/media36/thumbnail37/e2e38）
- [x] 4.1-4.6 六个前端任务各自验收通过（2026-08-29：P4 浏览器剧本完成；注册 A 发 3 篇含 WebP、登出、B 浏览/强刷、越权删除无入口、A 删除后首页刷新消失；修复 PublishView reactive 导入缺失）
- [x] P5 联调无阻塞、已知坑记录回填本表（2026-08-31：`mvn compile` 通过；/api/v1/ping 与 Vite `/api` 代理 200；auth/interceptor/media/thumbnail/e2e 共 75 断言全通过；三图 PNG+WebP 上传约 76ms、处理约 266ms；受限环境 Maven 本地仓库写权限问题已记录于 learning/10）
- [x] 6.5 compose 全栈一次拉起成功（2026-08-31：`docker compose config --quiet` 通过；`docker compose up -d --build` 后 MySQL healthy、backend healthy、Redis/RabbitMQ/MinIO/frontend 均 running，唯一对外端口为 :8081）
- [x] 6.6 冒烟剧本 7 步全过（2026-08-31：经 :8081 完成 ping、注册和 JWT TTL、上传、MQ 缩略图 status=1、发布、匿名 feed、`/minio` 缩略图反代 200、SPA `/note/:id` 200；浏览器首页新图片可见且控制台无 error）
- [x] README 状态表更新为"MVP 已部署"（2026-08-31）
- [x] P7 运维基线三件套可执行（2026-09-02：`backup-mysql.ps1 -Preview` 与实际备份均通过；`watch-backend-errors.ps1` 可过滤 backend 日志；`check-dlq.ps1` 经 Compose 内 `rabbitmqctl` 返回 `media.dlq=0`）
- [x] MVP 证据链复验归档（2026-09-02：Compose 重建成功；五组 API 黑盒脚本 75/75；后端 JUnit 5 单元测试 9/9；前端构建、首页/登录页和 console error 检查通过；证据与边界见 `docs/evidence/2026-09-02-MVP复验.md`）

</details>

---

## 附 2 · P8 改进阶段 Checklist（当前进度真相源）

> P8 的任务定义、文件边界和验收标准以 [04-产品与工程改进总纲](04-产品与工程改进总纲.md) 为准。完成项均由 [P8 全量验收证据](evidence/2026-09-03-P8改进验收.md) 支撑；P9 完成项由 [P9 社交最小闭环验收证据](evidence/2026-09-04-P9社交最小闭环验收.md) 支撑。

- [x] P8-01~P8-07 P0 止血：刷新重放、上传生命周期、媒体失败状态、注册竞态、发布幂等、配置 fail-fast、原图隐私（2026-09-03：前后端测试、黑盒矩阵和 P8 Compose 集成矩阵通过；默认展示缩略图；`ops/migrate-media-urls.ps1 -Preview` 提供历史直链迁移入口）
- [x] P8-08~P8-13 前端产品重构：视觉 token、错误/加载态、发布工作台、动态路由、响应式、无障碍、图片 fallback、草稿保护、前端测试（2026-09-03：5 个 Vitest 文件 12/12、Playwright 1/1、构建通过；in-app Browser 完成真实图片发布/详情/删除后只读核验）
- [x] P8-14~P8-18 后端工程补强：traceId、媒体可靠性、数据库约束、集成测试、Feed 缓存证据（2026-09-03：Docker Maven JUnit 20/20；P8 Compose 集成 6/6；V2/V3、Redis、RabbitMQ、Feed 并发证据通过）
- [x] P8-19~P8-22 发布运维：CI、备份恢复、镜像可复现、P8 证据报告（2026-09-03：备份恢复计数一致、失败恢复非零退出、镜像固定 digest、CI 检查无敏感信息命中；报告见 `docs/evidence/2026-09-03-P8改进验收.md`）
- [x] P8 出口门禁：390/768/1440 三个宽度浏览器剧本、后端/前端测试、Compose 配置检查、证据报告、HANDOVER/CHANGELOG 同步（2026-09-03：三宽度无水平溢出、console error=0；全部文档已同步）

---

## 附 3 · P9 社交最小闭环 Checklist

> P9 任务定义以 [05-后续开发路线图与实施手册](05-后续开发路线图与实施手册.md) §3 为准；完成项均由 [P9 验收证据](evidence/2026-09-04-P9社交最小闭环验收.md) 支撑。P9 完成后下一阶段为 P10 评论与通知。

- [x] P9-01 数据层：Flyway V4 新增 `note_likes`、`note_bookmarks`、`follows`，唯一键、外键和禁止自关注 CHECK 生效（2026-09-04：Compose 黑盒实查通过）
- [x] P9-02 后端接口：点赞/取消点赞、收藏/取消收藏、关注/取消关注、“我的收藏”游标列表（2026-09-04：黑盒 13/13、后端 JUnit 26/26）
- [x] P9-03 聚合视图：匿名/登录详情、Feed、公开主页返回统一 `social` 状态与关系计数（2026-09-04：黑盒覆盖匿名详情、登录 Feed、主页状态和私密/删除边界）
- [x] P9-04 前端交互：详情点赞/收藏、主页关注、收藏入口、匿名跳登录并保留回跳地址（2026-09-04：Vitest 15/15、构建 101 modules、浏览器详情/收藏路由只读检查）
- [x] P9-05 并发与幂等：20 次并发点赞/关注各只保留一条关系，重复取消不报错，计数与关系表一致（2026-09-04：P9 黑盒通过）
- [x] P9 出口门禁：后端/前端测试、Compose 重建与健康、黑盒矩阵、浏览器主流程、证据报告和文档闭环（2026-09-04：全部通过，下一步 P10）

---

## 附 4 · P10 评论与通知 Checklist

> P10 任务定义以 [05-后续开发路线图与实施手册](05-后续开发路线图与实施手册.md) §4 为准。完成项均由 [P10 验收证据](evidence/2026-09-04-P10评论与通知验收.md) 支撑；P10 完成后下一阶段为 P11 搜索与发现。

- [x] P10-01 契约与数据层：评论/通知 API、V5 migration、同笔记回复复合外键、同步通知方案（2026-09-04：V5 已应用，复合外键和同步事务方案实查通过）
- [x] P10-02 后端评论：一级评论/回复、500/300 字限制、纯文本校验、20 条/分钟限流、游标列表（2026-09-04：黑盒覆盖并通过）
- [x] P10-03 评论治理：作者软删除、幂等删除、权限边界、已删除占位（2026-09-04：黑盒覆盖并通过）
- [x] P10-04 通知闭环：点赞/关注/评论/回复同步生成通知、通知游标、未读数、批量已读（2026-09-04：黑盒覆盖并通过）
- [x] P10-05 前端交互：详情评论加载更多/提交中/失败重试/删除确认/空态/已删除占位/匿名引导（2026-09-04：Vitest、构建和浏览器只读详情核验通过）
- [x] P10-06 前端通知：导航入口未读数、通知列表和全部已读，轮询失败不阻塞主页面（2026-09-04：Vitest、构建和浏览器只读通知路由核验通过）
- [x] P10 出口门禁：后端/前端测试、Compose 重建与健康、黑盒矩阵、浏览器主流程、性能/安全边界、证据报告和文档闭环（2026-09-04：黑盒 17/17、后端 32/32、前端 21/21、构建 104 modules、Playwright 1/1，证据报告已归档）

### P10 未覆盖项补充 Checklist

> 本补充范围承接 [P10 评论与通知验收证据](evidence/2026-09-04-P10评论与通知验收.md) 的未覆盖边界；完成后追加一份补充证据，不回改历史验收原始结果。

- [x] P10-S01 基础敏感词拦截：评论服务端配置词表、命中不回显词项、前后端回归（依赖 `SCENARY_COMMENT_SENSITIVE_WORDS`；2026-09-04：黑盒 `18/18`，配置词表命中和统一 40000 通过）
- [x] P10-S02 通知实时推送：JWT 首帧认证、事务提交后 WebSocket 通知、断线回退轮询、Compose/Nginx 升级头（2026-09-04：真实 WebSocket 黑盒 `4/4`，前端断线重连/轮询降级单测通过）
- [x] P10-S03 跨浏览器：Playwright Chromium、Firefox、WebKit 对详情评论与通知路由回归，记录不可运行的浏览器原因（2026-09-04：三浏览器 `6/6`，无不可运行项）
- [x] P10-S04 读屏与键盘专项：详情评论和通知页可访问名称、焦点、键盘操作、动态状态提示回归（2026-09-04：三浏览器语义可访问性和 Tab 焦点 `6/6`；未执行实体读屏器脚本）
- [x] P10-S05 正式压力基线：可复现并发脚本、请求量与 p50/p95/p99、错误率、服务健康和边界说明（2026-09-04：10 并发/10 秒，`15561` 请求，错误率 `0`，p50/p95/p99 `5.75/11.49/16.64ms`）
- [x] P10-S 补充出口门禁：上述 5 项均有命令/环境/结果/边界证据，CHANGELOG、HANDOVER、README 与本 Checklist 对齐（2026-09-04：补充证据见 `docs/evidence/2026-09-04-P10未覆盖项补充验收.md`，学习笔记见 `docs/learning/17-P10补充验收与实时通道.md`）

---

## 附 5 · P11 搜索与发现 Checklist

> P11 任务定义以 [05-后续开发路线图与实施手册](05-后续开发路线图与实施手册.md) §5 为准。首期不引入 Elasticsearch、标签字段或推荐算法；完成项必须由 P11 证据报告支撑。

- [x] P11-01 契约与数据层：补充搜索 API v1.3、V6 读路径索引、空库/已有 V5 数据前向迁移验证（2026-09-04：已有库 V6 已应用；临时空 schema 由 Flyway 从 V1 到 V6 成功，4 个搜索索引存在）
- [x] P11-02 后端搜索：标题/正文/地点/作者昵称受控 LIKE、通配符转义、公开/软删/私密/禁用作者过滤、recent/relevance 排序（2026-09-04：黑盒字段/可见性/排序矩阵通过）
- [x] P11-03 游标与响应：opaque cursor 绑定 query/sort，翻页无重复，非法 cursor/sort 和 q 边界稳定返回 40000，highlight 纯文本（2026-09-04：黑盒 cursor、边界码和纯文本 highlight 通过）
- [x] P11-04 前端搜索：导航搜索入口、搜索页、query/sort、加载/空态/失败重试、加载更多、结果卡片和 URL 可分享（2026-09-04：Vitest、构建和 Chromium/Firefox/WebKit 专项通过）
- [x] P11-05 运维与性能：搜索索引重建/分析脚本可重跑，记录 EXPLAIN、样本量、p95、慢查询与回滚边界（2026-09-04：ANALYZE/EXPLAIN 通过；20 次样本 p95 17.14ms，回滚边界已记录）
- [x] P11 出口门禁：后端/前端测试、Compose/Flyway、黑盒可见性与分页矩阵、浏览器主流程、证据报告和文档闭环（2026-09-04：后端 37/37、前端 27/27、构建 107 modules、浏览器 3/3；证据见 `docs/evidence/2026-09-04-P11搜索与发现验收.md`）

---

## 附 6 · P12 视频与地点 Checklist

> P12 任务定义以 [05-后续开发路线图与实施手册](05-后续开发路线图与实施手册.md) §6 为准。基础链路与后续 P12-E1 分片上传体验优化均已完成；私有桶短时签名、外部逆地理编码和地图 UI 不在当前范围。

- [x] P12-01 契约与数据层：API v1.4、V7 媒体类型/视频状态/播放字段、notes 坐标字段，已有 V6 与空库迁移验证（2026-09-04：已有 V6 数据升级至 V7，V7=1 且 8 个扩展字段存在；本轮未执行 `down -v`）
- [x] P12-02 视频上传：容器/魔数校验、大小/时长/分辨率限制，原始对象不进响应（2026-09-04：黑盒魔数/处理中/原始路径断言通过；时长 120 秒、最长边 3840、大小 200MB 限制已由服务端实现）
- [x] P12-03 独立转码链路：`video.transcode` 队列与 DLQ、封面、480p/720p MP4、失败可观测/可重试，图片队列不被毒消息阻塞（2026-09-04：黑盒封面/720p/480p 可读、队列隔离通过；消费者失败先落 FAILED 再 nack 到 video.dlq，初次发现奇数宽度问题后已补齐偶数尺寸）
- [x] P12-04 地点隐私：手工/地图坐标与 EXIF 只读提取，公开响应不泄露原始 EXIF GPS（2026-09-04：MAP 坐标公开响应和 EXIF 候选坐标隐藏断言通过）
- [x] P12-05 前端体验：视频选择/处理中/失败重传/播放和地点字段；重复提交、刷新和离开页面边界有回归（2026-09-04：Vitest 31/31、构建 107 modules、三浏览器视频发布/转码等待/详情播放 3/3；提交中离开保护与单次发布请求有回归）
- [x] P12 出口门禁：后端/前端测试、Compose/ffmpeg、媒体队列隔离、EXIF 隐私、浏览器回归、证据报告和文档闭环（2026-09-04：基础链路后端 JUnit 39/39、P12 黑盒 11/11、Compose 重建/健康、ffmpeg/ffprobe 8.1.2 和证据/学习/交接文档均通过；P12-E1 另有独立 Checklist，现已完成）

### P12-E1 · 分片/预签名/断点续传体验优化

> E1 先收敛在视频上传，不改变已验收的图片代理上传与 P12 基础视频转码契约；V8 只新增会话/分片表，不修改 V7。

- [x] P12-E1-01 契约与数据层：API v1.5、V8 上传会话/分片表、owner 隔离、2 小时过期状态（2026-09-04：V8 已应用；黑盒 ①⑩⑪ 和后端单测覆盖会话状态、owner 隔离与过期码）
- [x] P12-E1-02 预签名直传：8MiB 分片、短时 PUT URL、MinIO 服务端 compose/copy、魔数/大小/分片完整性校验（2026-09-04：真实 MinIO 分片 PUT、合并/魔数校验黑盒 ②④⑧ 通过）
- [x] P12-E1-03 断点与生命周期：GET 恢复已上传分片、重复 complete 幂等、取消和过期会话/对象清理（2026-09-04：黑盒 ③⑤⑦⑨⑩ 全部通过）
- [x] P12-E1-04 前端体验：视频进度、刷新后重新选择同文件恢复、上传中离开保护、失败重试和单次 complete（2026-09-04：Vitest 33/33、构建 107 modules、三浏览器专项 3/3）
- [x] P12-E1 出口门禁：后端单测、Compose 黑盒真实分片 PUT/合并/转码、前端 Vitest/构建/三浏览器、证据/学习/HANDOVER/CHANGELOG 闭环（2026-09-04：后端 JUnit 50/50、黑盒 PASS=11 FAIL=0、Compose 重建/健康和文档闭环通过；证据见 `docs/evidence/2026-09-04-P12-E1分片上传验收.md`，学习笔记见 `docs/learning/20-P12-E1预签名与断点续传.md`）

---

## 附 7 · P12-E2 私有桶与短时签名 Checklist

> E2 任务定义以 [05 手册 §6.4](05-后续开发路线图与实施手册.md) 为准。实测依据见 [project-audit-2026-09-11](project-audit-2026-09-11.md) 探针 3/4（私密笔记封面匿名可达、桶级匿名存在性探测）。API 契约不变，URL 字段值改为短时签名。

- [x] E2-01 签名读能力：`MinioService.presignGet`/`viewUrl` 容错读、`SCENARY_MEDIA_PRESIGN_READ` 开关（false=回滚直链）、`ops/set-bucket-policy.ps1` 双向切换脚本、minio-init 策略经 `MINIO_BUCKET_POLICY`（默认 none=私有）（2026-09-12：minio-init 实测输出 `set to private`，黑盒 ③④⑤ 全 403）
- [x] E2-02 读路径切 key：媒体/笔记/搜索/社交/feed VO 改"持久化 key、运行时签名"（默认 TTL 300s 可配）；两级缓存改存 key、`FeedService.sign` 组装时签名；02 §1.5 增补"媒体 URL 生命周期"（2026-09-12：JUnit 68/68；黑盒 ②⑥ 签名 URL 全链路 + Range 206）
- [x] E2-03 历史数据回填：`ops/migrate-media-urls.ps1 -ToKeys` 支持 URL→key 解析回填（Preview→执行→抽查）（2026-09-12：Preview 命中 media 365+218 行、notes 218、users 26，执行后黑盒 ① 断言残留=0）
- [x] E2-04 关闭公开读与回归：桶切 private 后审计探针 3/4 复跑得 403；黑盒 ③ 直链 403、⑤ 存在性探测 403；视频/图片签名可读、Range 206；过期/防篡改语义由 ④ 篡改签名 403 覆盖；10 并发压测与三浏览器媒体链路（2026-09-12：E2 黑盒 8/8、13 套件回归、Playwright 6/6、压测错误率 0）
- [x] E2 出口门禁：黑盒/压测/浏览器/回滚路径（`set-bucket-policy.ps1 -Policy download` + `PRESIGN_READ=false`）均有命令与结果，证据报告落盘并同步 HANDOVER、CHANGELOG、05 §11（2026-09-12：证据见 `docs/evidence/2026-09-12-P12-E2私有桶与短时签名验收.md`）

---

## 附 8 · P12-E3 逆地理编码 Checklist

> E3 任务定义以 [05 手册 §6.5](05-后续开发路线图与实施手册.md) 为准。隐私边界：用户坐标禁止外发第三方公有 API，采用自托管容器；供应商关闭/超时/失败一律空候选降级。

- [ ] E3-01 provider SPI 与容器：`place` 包门面接口 + Nominatim/Photon 容器编排（01 §3.1 依赖行更新）、超时 2s、配置开关与关闭降级
- [ ] E3-02 API 与缓存：契约 v1.7 `GET /places/reverse-geocode`（登录用户、30/min 限流、geohash-5 缓存 TTL 30d）同步 02；JUnit + 黑盒覆盖边界码
- [ ] E3-03 前端联动：发布页选点/坐标输入触发候选地名回填（必须用户确认，不静默覆盖已填地名）；Vitest + 失败降级 UI
- [ ] E3-04 验收与隐私断言：容器无公网出联、缓存命中、限流 429、超时降级不阻塞发布；三浏览器回归
- [ ] E3 出口门禁：证据报告按 evidence 规范落盘并同步 Checklist、HANDOVER、CHANGELOG

---

## 附 9 · P12-E4 地图 UI Checklist

> E4 任务定义以 [05 手册 §6.6](05-后续开发路线图与实施手册.md) 为准，依赖 E3。不做路线/导航/附近推荐；瓦片源出网需在验收环境声明，未准备瓦片时降级为坐标文本。

- [ ] E4-01 依赖与组件：leaflet 登记 01 §3.1 并 dynamic import 拆包；地图选点组件（点击/拖 marker → lat/lng + place_source=MAP），`VITE_ENABLE_MAP` feature flag
- [ ] E4-02 发布页集成：选点 → E3 候选地名 → 可编辑确认；手工坐标输入保留为键盘可达替代路径
- [ ] E4-03 详情页展示：带坐标才渲染小地图，无坐标零布局抖动；390px 移动端适配
- [ ] E4-04 验收：Vitest、构建（leaflet 独立 chunk）、三浏览器、flag 关闭整体摘除回归、可访问性专项
- [ ] E4 出口门禁：证据报告落盘并同步 Checklist、HANDOVER、CHANGELOG

---

## 附 10 · P15 账号与安全基线 Checklist

> P15 任务定义以 [05 手册 §7](05-后续开发路线图与实施手册.md) 为准，2026-09-11 实测审计驱动立项。审计探针已正规化为 `docs/dev/test-p15-security.mjs` 作为本阶段回归工具。阈值以 02 §1.4 契约为准（注册默认 20/h/IP、Compose 演示 200/h；社交 120/min；发笔记 30/10min；上传 60/10min）。

- [x] P15-01 写接口限流基线：`common.RateLimitService`（复用评论 INCR 模式与 TOO_MANY_REQUESTS 码）+ XFF 末段归因与回环豁免；黑盒 429 断言（2026-09-12：社交 `{"404":120,"429":5}`、评论 22 触发、注册 201 触发并清理计数键恢复；JUnit RateLimitServiceTest/ClientIpTest 通过）
- [x] P15-02 安全响应头：nginx 增加 X-Frame-Options/X-Content-Type-Options/Referrer-Policy；CSP Report-Only + report-to 指令（/csp-report 丢弃收集，WebKit console 断言抓到缺指令缺陷后补齐）；HSTS 仅 TLS 部署形态启用并登记（2026-09-12：黑盒 ① 四头齐全 + http 无 HSTS 预期）
- [x] P15-03a 密码找回（运维重置脚本）：`ops/reset-user-password.ps1`（jshell + spring-security-crypto 生成 BCrypt，支持 -Preview/-Reactivate 与自定义 Maven 仓库）（2026-09-12：端到端实测旧密 40000/新密 code=0）
- [ ] P15-03b 邮件找回（延后）：V9 users.email + 重置令牌表（15min 单次）+ spring-boot-starter-mail（01 §3.1 已登记）；依赖外部 SMTP 凭据，当前以 03a 过渡
- [x] P15-04 账号注销：DELETE /users/me（密码二次确认）→ status=2 + 昵称匿名化 + 笔记评论软删 + 令牌吊销 + 用户名保留防冒名；契约进 02 v1.6 并对齐禁用账号可见性边界（2026-09-12：黑盒 ④ 六断言通过；前端危险区 Vitest 2 用例；bean 环解耦沉淀学习笔记 21）
- [x] P15-05 通知生命周期：定时清理已读超 90 天通知（@Scheduled 分批 500，env 可关），dry-run 统计门面 + 配置注入断言（2026-09-12：JUnit 分批/统计用例 + 黑盒 ⑥ env 注入通过）
- [x] P15-06 仓库卫生：根 `/test-results/` 入 .gitignore（v2.39 生效）；审计探针正规化为 `docs/dev/test-p15-security.mjs`（随机用户名 + 限流键清理，可重复执行）
- [x] P15 出口门禁：探针复跑矩阵（注册 429、安全头齐全、注销后旧令牌 401/登录 40301/内容 404、重置链路端到端）+ 既有矩阵无回归；证据落盘并同步 HANDOVER、CHANGELOG、05 §11（2026-09-12：JUnit 68/68、Vitest 35/35、构建 107 modules、12 黑盒套件 147/147 + P15 黑盒 13/13、三浏览器 6/6；证据见 `docs/evidence/2026-09-12-P15账号与安全基线验收.md`）
