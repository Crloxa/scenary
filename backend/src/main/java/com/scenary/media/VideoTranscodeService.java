package com.scenary.media;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 由独立视频消费者调用的 ffprobe/ffmpeg 转码门面。 */
@Service
public class VideoTranscodeService {

    static final long MAX_DURATION_MS = 120_000L;
    static final int MAX_DIMENSION = 3_840;
    private static final int TOOL_TIMEOUT_SECONDS = 120;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final MinioService minio;
    private final MediaMapper mediaMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoTranscodeService(MinioService minio, MediaMapper mediaMapper) {
        this.minio = minio;
        this.mediaMapper = mediaMapper;
    }

    public void transcode(long mediaId) throws Exception {
        MediaEntity media = mediaMapper.findById(mediaId);
        if (media == null) {
            throw new IllegalStateException("video media row missing");
        }
        if (media.getMediaType() == null || media.getMediaType() != MediaType.VIDEO.code()
                || media.getStatus() == null || media.getStatus() != MediaService.VIDEO_PROCESSING) {
            return;
        }

        Path workDir = Files.createTempDirectory("scenary-video-");
        List<String> uploadedKeys = new ArrayList<>();
        try {
            Path input = workDir.resolve("input.bin");
            try (InputStream source = minio.get(media.getObjectKey())) {
                Files.copy(source, input, StandardCopyOption.REPLACE_EXISTING);
            }

            VideoMetadata metadata = probe(input);
            validate(metadata);

            Path cover = workDir.resolve("cover.jpg");
            Path high = workDir.resolve("playback-720.mp4");
            Path low = workDir.resolve("playback-480.mp4");
            runTool(List.of("ffmpeg", "-y", "-v", "error", "-i", input.toString(),
                    "-frames:v", "1", "-vf", "scale=800:720:force_original_aspect_ratio=decrease",
                    "-q:v", "3", cover.toString()));
            transcodeVariant(input, high, 1280, 720);
            transcodeVariant(input, low, 854, 480);

            String base = "video/" + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID();
            String coverKey = "thumb/" + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID() + "_t.jpg";
            String highKey = base + "_720.mp4";
            String lowKey = base + "_480.mp4";
            put(coverKey, cover, "image/jpeg");
            uploadedKeys.add(coverKey);
            put(highKey, high, "video/mp4");
            uploadedKeys.add(highKey);
            put(lowKey, low, "video/mp4");
            uploadedKeys.add(lowKey);

            int updated = mediaMapper.updateVideoProcessResult(mediaId, coverKey,
                    metadata.width(), metadata.height(), metadata.durationMs(),
                    highKey, lowKey);
            if (updated != 1) {
                throw new IllegalStateException("video status changed before completion");
            }
        } catch (Exception e) {
            for (String key : uploadedKeys) {
                try {
                    minio.remove(key);
                } catch (Exception ignored) {
                    // 失败对象由后续媒体清理任务兜底，不覆盖原始转码错误。
                }
            }
            throw e;
        } finally {
            deleteWorkDir(workDir);
        }
    }

    private void transcodeVariant(Path input, Path output, int width, int height)
            throws Exception {
        runTool(List.of("ffmpeg", "-y", "-v", "error", "-i", input.toString(),
                "-map", "0:v:0", "-map", "0:a?", "-vf",
                "scale=" + width + ":" + height
                        + ":force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
                "-pix_fmt", "yuv420p", output.toString()));
    }

    private VideoMetadata probe(Path input) throws Exception {
        String output = runTool(List.of("ffprobe", "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration", "-of", "json",
                input.toString()));
        JsonNode root = objectMapper.readTree(output);
        JsonNode stream = root.path("streams").path(0);
        int width = stream.path("width").asInt(0);
        int height = stream.path("height").asInt(0);
        double seconds = root.path("format").path("duration").asDouble(0);
        if (width <= 0 || height <= 0 || !Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("video metadata incomplete");
        }
        return new VideoMetadata(width, height, Math.round(seconds * 1000));
    }

    private void validate(VideoMetadata metadata) {
        if (metadata.durationMs() > MAX_DURATION_MS) {
            throw new IllegalArgumentException("video duration exceeds limit");
        }
        if (metadata.width() > MAX_DIMENSION || metadata.height() > MAX_DIMENSION) {
            throw new IllegalArgumentException("video resolution exceeds limit");
        }
    }

    private void put(String key, Path file, String contentType) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            minio.put(key, input, Files.size(file), contentType);
        }
    }

    private String runTool(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
        boolean finished = process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            outputFuture.cancel(true);
            throw new IllegalStateException("video tool timeout");
        }
        String output = outputFuture.get(5, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("video tool failed exit=" + process.exitValue());
        }
        return output;
    }

    private void deleteWorkDir(Path workDir) {
        try (var paths = Files.walk(workDir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // 临时文件清理失败不改变已持久化的业务状态。
                }
            });
        } catch (Exception ignored) {
            // 同上。
        }
    }

    record VideoMetadata(int width, int height, long durationMs) {
    }
}
