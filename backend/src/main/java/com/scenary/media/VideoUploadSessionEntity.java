package com.scenary.media;

import java.util.Date;

import lombok.Data;

@Data
public class VideoUploadSessionEntity {

    private String uploadId;
    private Long userId;
    private String originalName;
    private String declaredMime;
    private Long sizeBytes;
    private Integer totalParts;
    private Integer status;
    private Long completedMediaId;
    private Date expiresAt;
    private Date createdAt;
    private Date updatedAt;
}
