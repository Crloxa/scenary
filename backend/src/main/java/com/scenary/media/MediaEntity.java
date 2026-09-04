package com.scenary.media;

import java.util.Date;

import lombok.Data;

/**
 * media 表实体，仅 media 模块与经门面约定的模块可见（docs/01 §4.1）。
 */
@Data
public class MediaEntity {

    private Long id;
    private Long userId;
    private Long noteId;
    private Integer orderNo;
    private String bucket;
    private String objectKey;
    private String url;
    private String thumbObjectKey;
    private String thumbUrl;
    private String mime;
    /** 0 图片，1 视频。 */
    private Integer mediaType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private Long durationMs;
    private String playbackObjectKey;
    private String playbackUrl;
    private String playbackLowObjectKey;
    private String playbackLowUrl;
    private java.math.BigDecimal exifLatitude;
    private java.math.BigDecimal exifLongitude;
    /** 0处理中 1完成 2失败（docs/02 §4.2 状态机） */
    private Integer status;
    private String failureReason;
    private Date failedAt;
    private Integer publishStatus;
    private Integer publishAttempts;
    private Date lastPublishAt;
    private Date createdAt;
    private Date updatedAt;
}
