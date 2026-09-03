package com.scenary.media;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.scenary.config.MinioProperties;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

/**
 * 对象存储薄封装：业务层只见 key，不感知 endpoint 与桶策略。
 */
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties props;

    public MinioService(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
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

    /** 默认展示 URL：优先缩略图；仅在显式配置 exposeOriginalUrl=true 时暴露原图。 */
    public String displayUrl(MediaEntity media) {
        if (media == null) {
            return null;
        }
        if (props.isExposeOriginalUrl()) {
            return media.getUrl();
        }
        if (media.getThumbUrl() != null && !media.getThumbUrl().isBlank()) {
            return media.getThumbUrl();
        }
        return publicUrl(thumbKeyOf(media.getObjectKey()));
    }

    public String displayUrl(String objectKey) {
        if (props.isExposeOriginalUrl()) {
            return publicUrl(objectKey);
        }
        return publicUrl(thumbKeyOf(objectKey));
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
}
