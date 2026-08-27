package com.scenary.media;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.MinioProperties;
import com.scenary.config.RabbitConfig;

/**
 * 上传管线（docs/01 §5.2）：嗅探魔数 -> 存原图 -> 建 media(status=0) -> 发 media.uploaded 即返回。
 * 消息发送失败不回滚上传：媒体停留在 PROCESSING 由人工/清理策略兜底，避免整批作废。
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final MinioProperties minioProps;
    private final MinioService minio;
    private final MediaMapper mediaMapper;
    private final RabbitTemplate rabbitTemplate;

    public MediaService(MinioProperties minioProps, MinioService minio,
                        MediaMapper mediaMapper, RabbitTemplate rabbitTemplate) {
        this.minioProps = minioProps;
        this.minio = minio;
        this.mediaMapper = mediaMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public MediaUploadVO uploadImages(long userId, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 9) {
            throw new BizException(ErrorCode.VALIDATION, "图片数量须为 1~9 张");
        }
        String month = MONTH.format(LocalDate.now());
        List<MediaItemVO> items = new ArrayList<>(files.size());
        int order = 1;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BizException(ErrorCode.VALIDATION, "包含空文件");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new BizException(ErrorCode.VALIDATION, "单张图片不能超过 10MB");
            }
            MediaEntity media = store(userId, file, order++, month);
            publishUploaded(media.getId());
            items.add(new MediaItemVO(media.getId(), media.getUrl(), null,
                    media.getStatus(), null, null));
        }
        return new MediaUploadVO(items);
    }

    public MediaItemVO getStatus(long userId, long mediaId) {
        MediaEntity media = requireOwned(userId, mediaId);
        return new MediaItemVO(media.getId(), media.getUrl(),
                media.getThumbUrl(), media.getStatus(), media.getWidth(), media.getHeight());
    }

    public void deleteUnbound(long userId, long mediaId) {
        MediaEntity media = requireOwned(userId, mediaId);
        if (media.getNoteId() != null) {
            throw new BizException(ErrorCode.VALIDATION, "已绑定笔记的媒体请通过删除笔记处理");
        }
        // 对象文件按契约保留（docs/02 §4.3），物理清理属于二期后台任务
        mediaMapper.deleteUnbound(mediaId, userId);
    }

    private MediaEntity requireOwned(long userId, long mediaId) {
        MediaEntity media = mediaMapper.findById(mediaId);
        if (media == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (media.getUserId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return media;
    }

    private MediaEntity store(long userId, MultipartFile file, int order, String month) {
        try (InputStream raw = file.getInputStream()) {
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(MediaImageType.sniffBytes() + 1);
            MediaImageType type = MediaImageType.detect(MediaImageType.readHead(in));
            in.reset();
            if (type == null) {
                throw new BizException(ErrorCode.VALIDATION,
                        "仅支持 jpeg/png/webp/gif 图片，且文件内容须与扩展名一致");
            }
            String key = "orig/" + month + "/" + UUID.randomUUID() + "." + type.ext();
            minio.put(key, in, file.getSize(), type.mime());

            MediaEntity media = new MediaEntity();
            media.setUserId(userId);
            media.setOrderNo(order);
            media.setBucket(minioProps.getBucket());
            media.setObjectKey(key);
            media.setUrl(minio.publicUrl(key));
            media.setMime(type.mime());
            media.setSizeBytes(file.getSize());
            media.setStatus(0);
            mediaMapper.insert(media);
            return media;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("store image failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片处理失败");
        }
    }

    private void publishUploaded(long mediaId) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_MEDIA_EVENT,
                    RabbitConfig.RK_MEDIA_UPLOADED, Map.of("mediaId", mediaId));
        } catch (Exception e) {
            // 消息黑洞兜底：media 停留 status=0 可观测（轮询接口），不会假装成功
            log.error("publish media.uploaded failed, mediaId={}", mediaId, e);
        }
    }
}
