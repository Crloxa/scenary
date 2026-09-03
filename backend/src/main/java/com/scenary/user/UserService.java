package com.scenary.user;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.GridCardVO;
import com.scenary.common.PageResult;
import com.scenary.media.MediaImageType;
import com.scenary.media.MinioService;
import com.scenary.note.NoteService;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

/**
 * 用户域服务：资料/头像/公开主页；个人网格经 NoteService 门面取数（依赖铁律 docs/01 §4.1）。
 * 头像为"封面式"200x200 居中裁切，输出 JPEG 单对象，无独立缩略图（docs/02 §3.3）。
 */
@Service
public class UserService {

    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;

    private final UserMapper userMapper;
    private final MinioService minio;
    private final NoteService noteService;

    public UserService(UserMapper userMapper, MinioService minio, NoteService noteService) {
        this.userMapper = userMapper;
        this.minio = minio;
        this.noteService = noteService;
    }

    public UserVO me(long userId) {
        UserEntity u = require(userId);
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(),
                u.getBio(), userMapper.countNotes(userId, true), u.getCreatedAt().getTime());
    }

    /** 不存在或禁用账号一律按资源不存在表达（禁用者登录已在认证域拦截） */
    private UserEntity require(long userId) {
        UserEntity u = userMapper.findById(userId);
        if (u == null || (u.getStatus() != null && u.getStatus() == 0)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return u;
    }

    public PublicUserVO publicProfile(long userId) {
        UserEntity u = require(userId);
        return new PublicUserVO(u.getId(), u.getNickname(), u.getAvatarUrl(),
                u.getBio(), userMapper.countNotes(userId, false), u.getCreatedAt().getTime());
    }

    public UserVO updateProfile(long userId, ProfileUpdateRequest req) {
        if ((req.nickname() == null || req.nickname().isBlank())
                && (req.bio() == null || req.bio().isBlank())) {
            throw new BizException(ErrorCode.VALIDATION, "昵称与签名至少提供一项");
        }
        userMapper.updateProfile(userId,
                (req.nickname() == null || req.nickname().isBlank()) ? null : req.nickname(),
                req.bio());
        return me(userId);
    }

    public String updateAvatar(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION, "缺少文件");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new BizException(ErrorCode.VALIDATION, "头像不能超过 5MB");
        }
        try {
            MediaImageType type = MediaImageType.detect(
                    MediaImageType.readHead(file.getInputStream()));
            // 头像契约只允许 jpeg/png；JDK 原生不支持 WebP 重采样，避免先存入不可用对象。
            if (type == null || type == MediaImageType.GIF || type == MediaImageType.WEBP) {
                throw new BizException(ErrorCode.VALIDATION, "头像仅支持 jpeg/png");
            }
            var img = ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new BizException(ErrorCode.VALIDATION, "图片内容无法解码");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(img).size(200, 200).crop(Positions.CENTER)
                    .outputFormat("jpg").toOutputStream(out);

            String key = "avatar/" + userId + "/" + UUID.randomUUID() + ".jpg";
            minio.put(key, new ByteArrayInputStream(out.toByteArray()), out.size(), "image/jpeg");
            String url = minio.publicUrl(key);
            userMapper.updateAvatar(userId, url);
            return url;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "头像处理失败");
        }
    }

    public PageResult<GridCardVO> gridNotes(long targetUserId, Long viewerId,
                                            Long cursorParam, Integer limitParam) {
        return noteService.gridNotes(targetUserId, viewerId, cursorParam, limitParam);
    }
}
