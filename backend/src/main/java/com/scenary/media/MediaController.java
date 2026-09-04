package com.scenary.media;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final VideoUploadService videoUploadService;

    public MediaController(MediaService mediaService, VideoUploadService videoUploadService) {
        this.mediaService = mediaService;
        this.videoUploadService = videoUploadService;
    }

    /** multipart 字段名固定 files，可重复多个（docs/02 §4.1） */
    @PostMapping("/images")
    public Result<MediaUploadVO> upload(
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Result.ok(mediaService.uploadImages(UserContext.require(), files));
    }

    @PostMapping("/videos")
    public Result<MediaUploadVO> uploadVideo(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return Result.ok(mediaService.uploadVideo(UserContext.require(), file));
    }

    @PostMapping("/video-uploads")
    public Result<VideoUploadSessionVO> createVideoUpload(@RequestBody VideoUploadCreateRequest request) {
        return Result.ok(videoUploadService.create(UserContext.require(), request));
    }

    @GetMapping("/video-uploads/{uploadId}")
    public Result<VideoUploadSessionVO> videoUploadStatus(@PathVariable String uploadId) {
        return Result.ok(videoUploadService.get(UserContext.require(), uploadId));
    }

    @PostMapping("/video-uploads/{uploadId}/parts/{partNumber}/url")
    public Result<VideoUploadPartUrlVO> videoUploadPartUrl(@PathVariable String uploadId,
                                                            @PathVariable int partNumber) {
        return Result.ok(videoUploadService.presignPart(UserContext.require(), uploadId, partNumber));
    }

    @PostMapping("/video-uploads/{uploadId}/complete")
    public Result<MediaUploadVO> completeVideoUpload(@PathVariable String uploadId) {
        return Result.ok(videoUploadService.complete(UserContext.require(), uploadId));
    }

    @DeleteMapping("/video-uploads/{uploadId}")
    public Result<Void> cancelVideoUpload(@PathVariable String uploadId) {
        videoUploadService.cancel(UserContext.require(), uploadId);
        return Result.ok();
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
