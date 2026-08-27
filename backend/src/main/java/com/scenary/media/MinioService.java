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
