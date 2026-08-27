package com.scenary.media;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.scenary.auth.UserContext;
import com.scenary.common.Result;

/**
 * 媒体接口。受保护路径由 WebConfig 拦截（/api/v1/media/**），userId 一律取自 UserContext。
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** multipart 字段名固定 files，可重复多个（docs/02 §4.1） */
    @PostMapping("/images")
    public Result<MediaUploadVO> upload(
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Result.ok(mediaService.uploadImages(UserContext.require(), files));
    }

    @GetMapping("/{mediaId}")
    public Result<MediaItemVO> status(@PathVariable long mediaId) {
        return Result.ok(mediaService.getStatus(UserContext.require(), mediaId));
    }

    @DeleteMapping("/{mediaId}")
    public Result<Void> delete(@PathVariable long mediaId) {
        mediaService.deleteUnbound(UserContext.require(), mediaId);
        return Result.ok();
    }
}
