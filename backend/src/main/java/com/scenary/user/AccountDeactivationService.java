package com.scenary.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scenary.comment.CommentService;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.media.MinioService;
import com.scenary.note.NoteService;

/**
 * 账号注销编排（docs/02 §3.8，P15-04）。独立于 UserService：CommentService 反向依赖
 * UserService（ensureActive/author），若由 UserService 直接调用评论门面会形成 bean 环。
 * 令牌吊销是 Redis 操作，由 Controller 在本事务提交后调用 auth 门面完成，不入库内事务。
 */
@Service
public class AccountDeactivationService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MinioService minio;
    private final NoteService noteService;
    private final CommentService commentService;

    public AccountDeactivationService(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder,
                                      MinioService minio, NoteService noteService,
                                      CommentService commentService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.minio = minio;
        this.noteService = noteService;
        this.commentService = commentService;
    }

    @Transactional
    public void deactivate(long userId, String password) {
        UserEntity u = userMapper.findById(userId);
        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (password == null || password.isBlank()
                || !passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new BizException(ErrorCode.FORBIDDEN, "密码确认失败");
        }
        if (userMapper.deactivate(userId) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        removeAvatarObject(u.getAvatarUrl());
        noteService.deactivateAuthorNotes(userId);
        commentService.deactivateAuthorComments(userId);
    }

    private void removeAvatarObject(String avatarUrl) {
        try {
            String key = minio.objectKeyFromPublicUrl(avatarUrl);
            if (key != null) {
                minio.remove(key);
            }
        } catch (Exception e) {
            // 头像对象删除失败不阻断注销；对象残留由媒体清理任务兜底
        }
    }
}
