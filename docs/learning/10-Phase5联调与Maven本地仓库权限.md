# Phase 5 联调与 Maven 本地仓库权限

## 现象

Phase 5 启动后，后端曾在 Maven 编译阶段报告“unknown compilation problem”，日志同时出现本地仓库文件访问被拒绝；前端代理和后端服务尚未进入联调。

## 原因

当前受限执行环境无法写入 Maven 本地仓库中的 resolver 状态文件。项目的 JDK 21、Spring Boot 3.5.16 及依赖版本本身没有解析冲突。

## 原理

Maven 编译不只读取依赖包，还会更新本地仓库的解析状态与插件元数据。仓库写权限不足时，编译器输出可能只剩笼统的编译失败，而不是明确的源码错误。

## 我怎么验证的

在允许 Maven 写入本地缓存的受控权限下执行 `mvn compile`，59 个 Java 源文件编译成功。随后启动 MySQL、Redis、RabbitMQ、MinIO 和 Spring Boot，`/api/v1/ping` 返回 200；认证、拦截、媒体、缩略图、端到端脚本分别通过 18、8、13、7、29 条断言。三图（PNG、PNG、WebP）上传耗时约 76ms，全部处理完成约 266ms；Vite 首页与 `/api` 代理均返回 200。

## 可复用结论

先区分 Maven 本地仓库权限问题和源码编译问题：检查 `mvn -version`、JDK 版本及日志中的仓库访问错误，再在具备缓存写权限的环境中重跑标准 `mvn compile`。Phase 5 联调应同时覆盖服务健康、代理转发、异常路径和真实多图处理耗时。
