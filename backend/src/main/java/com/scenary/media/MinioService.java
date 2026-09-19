package com.scenary.media;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.scenary.config.MinioProperties;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;

/**
 * 对象存储薄封装：业务层只见 key，不感知 endpoint 与桶策略。
 */
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final MinioClient presignClient;

    public MinioService(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
        this.presignClient = buildPresignClient(minioClient, props);
    }

    public void put(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "对象存储写入失败");
        }
    }

    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "对象存储读取失败");
        }
    }

    /** 浏览器可达的直链前缀：{publicHost}/{bucket}/{key}（生产指向 nginx 反代路径） */
    public String publicUrl(String objectKey) {
        return props.getPublicHost() + "/" + props.getBucket() + "/" + objectKey;
    }

    /**
     * 从持久化的公开直链反解 object key（形如 {publicHost}/{bucket}/{key}）；
     * 不匹配既有前缀格式时返回 null，由调用方决定兜底。
     */
    public String objectKeyFromPublicUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String marker = "/" + props.getBucket() + "/";
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String key = url.substring(idx + marker.length());
        int query = key.indexOf('?');
        return query < 0 ? key : key.substring(0, query);
    }

    /** 默认展示 URL：优先缩略图；仅在显式配置 exposeOriginalUrl=true 时暴露原图。E2 起经 viewUrl 短时签名。 */
    public String displayUrl(MediaEntity media) {
        if (media == null) {
            return null;
        }
        if (props.isExposeOriginalUrl()) {
            return viewUrl(media.getUrl() != null ? media.getUrl() : media.getObjectKey());
        }
        if (media.getThumbObjectKey() != null && !media.getThumbObjectKey().isBlank()) {
            return viewUrl(media.getThumbObjectKey());
        }
        if (media.getThumbUrl() != null && !media.getThumbUrl().isBlank()) {
            return viewUrl(media.getThumbUrl());
        }
        return viewUrl(thumbKeyOf(media.getObjectKey()));
    }

    public String displayUrl(String objectKey) {
        if (props.isExposeOriginalUrl()) {
            return viewUrl(objectKey);
        }
        return viewUrl(thumbKeyOf(objectKey));
    }

    /** orig/{yyyyMM}/{uuid}.{ext} -> thumb/{yyyyMM}/{uuid}_t.jpg（与原图同 uuid 成对） */
    public static String thumbKeyOf(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("thumb/")) {
            return objectKey;
        }
        int slash = objectKey.indexOf('/');
        if (slash < 0) {
            return objectKey;
        }
        String withoutPrefix = objectKey.substring(slash + 1);
        int dot = withoutPrefix.lastIndexOf('.');
        return "thumb/" + (dot > 0 ? withoutPrefix.substring(0, dot) : withoutPrefix) + "_t.jpg";
    }

    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "对象存储删除失败");
        }
    }

    public String presignPut(String objectKey, int expirySeconds) {
        try {
            String signed = presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
            return addPathPrefix(signed, props.getPresignPathPrefix());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "预签名上传地址生成失败");
        }
    }

    /** E2 私有桶读路径：短时预签名 GET，复用 E1 的同源反代机制（docs/02 §1.5）。 */
    public String presignGet(String objectKey, int expirySeconds) {
        try {
            String signed = presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
            return addPathPrefix(signed, props.getPresignPathPrefix());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "预签名访问地址生成失败");
        }
    }

    /**
     * 展示 URL 的统一出口（E2-02）：入参是持久化值——新数据为 object key，
     * E2 切换前的历史数据是直链 URL（回填前容忍）。presign-read 关闭时原样返回（回滚模式）。
     */
    public String viewUrl(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!props.isPresignRead()) {
            return stored;
        }
        String key = stored;
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            key = objectKeyFromPublicUrl(stored);
            if (key == null) {
                return stored;   // 无法归因到本桶的旧值原样返回，交由调用方展示
            }
        }
        return presignGet(key, props.getPresignTtlSeconds());
    }

    public long statSize(String objectKey) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(props.getBucket()).object(objectKey).build()).size();
        } catch (Exception e) {
            return -1;
        }
    }

    public void compose(String objectKey, List<String> sourceKeys) {
        try {
            List<ComposeSource> sources = sourceKeys.stream()
                    .map(source -> ComposeSource.builder().bucket(props.getBucket()).object(source).build())
                    .toList();
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(props.getBucket()).object(objectKey).sources(sources).build());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "视频分片合并失败");
        }
    }

    public void copy(String sourceKey, String objectKey) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(props.getBucket()).object(objectKey)
                    .source(CopySource.builder().bucket(props.getBucket()).object(sourceKey).build())
                    .build());
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "视频对象归档失败");
        }
    }

    private MinioClient buildPresignClient(MinioClient fallback, MinioProperties properties) {
        if (properties.getPresignEndpoint() == null || properties.getPresignEndpoint().isBlank()
                || properties.getPresignEndpoint().equals(properties.getEndpoint())) {
            return fallback;
        }
        return MinioClient.builder()
                .endpoint(properties.getPresignEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region("us-east-1")
                .build();
    }

    private String addPathPrefix(String signedUrl, String prefix) {
        if (prefix == null || prefix.isBlank()) return signedUrl;
        java.net.URI uri = java.net.URI.create(signedUrl);
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return uri.getScheme() + "://" + uri.getRawAuthority() + normalized + path + query;
    }
}
