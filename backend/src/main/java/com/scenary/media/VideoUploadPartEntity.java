package com.scenary.media;

import java.util.Date;

import lombok.Data;

@Data
public class VideoUploadPartEntity {

    private String uploadId;
    private Integer partNumber;
    private String objectKey;
    private Long sizeBytes;
    private String etag;
    private Date createdAt;
    private Date updatedAt;
}
