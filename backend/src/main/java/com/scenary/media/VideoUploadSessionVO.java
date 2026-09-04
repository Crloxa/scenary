package com.scenary.media;

import java.util.List;

public record VideoUploadSessionVO(
        String uploadId,
        Integer chunkSize,
        Integer totalParts,
        Integer status,
        Long expiresAt,
        List<VideoUploadPartVO> uploadedParts) {
}
