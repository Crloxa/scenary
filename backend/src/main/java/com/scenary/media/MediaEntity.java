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
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    /** 0处理中 1完成 2失败（docs/02 §4.2 状态机） */
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
