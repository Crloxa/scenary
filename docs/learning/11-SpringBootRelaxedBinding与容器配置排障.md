# Spring Boot Relaxed Binding 与容器配置排障

## 现象

Phase 6 第一次全栈拉起时，Redis 不断重启，后端因 JWT secret 为 null 退出。修正后后端虽然变为 healthy，但注册接口报 MyBatis `Invalid bound statement`；再往后，上传和缩略图完成了，浏览器经 `/minio` 取图片却得到 Nginx 404。

## 原因

`application-dev.yml` 承载了 JWT、MyBatis XML、RabbitMQ listener 和上传限额等配置，但容器运行的是 `prod` profile，不能继承 dev 文件。Compose 最初把 `JWT_SECRET` 放在错误配置树根部，无法绑定到 `scenary.jwt.secret`；MyBatis 配置也完全缺失。Redis 参数则被 `sh` 当成自身参数，而不是传给 `redis-server`。最后，Nginx 的静态图片正则比普通 `/minio/` 前缀 location 优先，拦截了对象存储请求。

## 原理

Spring Boot relaxed binding 会把全大写下划线环境变量映射为配置路径，例如 `SCENARY_JWT_SECRET` 映射为 `scenary.jwt.secret`，`MYBATIS_MAPPER_LOCATIONS` 映射为 `mybatis.mapper-locations`。映射不代表 dev profile 的未覆盖项会自动存在，因此生产编排必须覆盖所有运行所需的配置树。Nginx 选择 location 时，正则 location 会覆盖普通最长前缀；使用 `^~ /minio/` 可阻止继续匹配静态资源正则。Docker command 的 shell 只负责展开变量，实际可执行程序仍须明确为 `redis-server`。

## 我怎么验证的

执行 `docker compose config --quiet` 后以 `docker compose up -d --build` 拉起全栈，`docker compose ps` 显示 backend healthy、MySQL healthy，Redis 与其他服务稳定 running。经 `http://localhost:8081/api/v1` 注册用户，验证 access/refresh TTL，上传 PNG 并轮询到 status=1，随后发布笔记且匿名 feed 可见。缩略图经 `/minio` 返回 HTTP 200，SPA `/note/:id` 返回 HTTP 200；浏览器首页中本次容器生成的缩略图具有非零 naturalWidth/naturalHeight，控制台没有 error。

## 可复用结论

把本地 dev 配置迁移到容器时，先按 `@ConfigurationProperties` 前缀列出必需项，再把普通 Spring、MyBatis 和 listener 配置逐项补到 Compose；不要只补第一个启动异常。Nginx 同时有代理和静态资源正则时，要显式检查 location 优先级。以真实注册、上传、异步消费、对象反代和浏览器渲染做闭环验收，才能发现健康检查无法覆盖的配置缺口。
