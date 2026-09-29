# MinIO mc 客户端镜像：自官方 GitHub 发布二进制构建（用途与版本对齐 docker/minio-server）。
# minio-init 的 command 内通过 command -v mc 定位二进制（/usr/bin/mc）。
FROM alpine:3.20
RUN apk add --no-cache bash
ADD https://dl.min.io/client/mc/release/linux-amd64/archive/mc.RELEASE.2025-08-13T08-35-41Z /usr/bin/mc
RUN chmod +x /usr/bin/mc
ENTRYPOINT ["/bin/bash", "-c"]
