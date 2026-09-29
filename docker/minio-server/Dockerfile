# MinIO 服务器镜像：自官方 GitHub 发布二进制构建。
# 背景：MinIO 已从 Docker Hub 移除官方镜像（minio/minio 仓库不可拉取，
# mirror.gcr.io 等缓存代理亦不可靠，CHANGELOG v2.48），改为源码级 vendor。
# 二进制版本与本地验收基线一致（docs/evidence/2026-09-28-E5地图浏览验收.md）。
# dl.min.io 为 MinIO 官方 CDN；ADD 远程资源无法 digest 固定，升级时人工核对官方 sha256。
FROM alpine:3.20
RUN addgroup -S minio && adduser -S minio -G minio
ADD https://dl.min.io/server/minio/release/linux-amd64/archive/minio.RELEASE.2025-09-07T16-13-09Z /usr/bin/minio
RUN chmod +x /usr/bin/minio
USER root
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
