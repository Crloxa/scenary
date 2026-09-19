package com.scenary.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.scenary.comment.CommentService;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.media.MinioService;
import com.scenary.note.NoteService;

@ExtendWith(MockitoExtension.class)
class AccountDeactivationServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private MinioService minio;
    @Mock
    private NoteService noteService;
    @Mock
    private CommentService commentService;

    private AccountDeactivationService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeactivationService(userMapper, passwordEncoder, minio,
                noteService, commentService);
    }

    private UserEntity activeUser() {
        UserEntity u = new UserEntity();
        u.setId(7L);
        u.setUsername("hill_walker");
        u.setPasswordHash("$2a$hash");
        u.setNickname("山野行人");
        u.setStatus(1);
        return u;
    }

    @Test
    void deactivateRequiresMatchingPassword() {
        when(userMapper.findById(7L)).thenReturn(activeUser());
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.deactivate(7L, "wrong"));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(userMapper, never()).deactivate(anyLong());
        verify(noteService, never()).deactivateAuthorNotes(anyLong());
    }

    @Test
    void deactivateAnonymizesSoftDeletesAndRemovesAvatar() {
        UserEntity u = activeUser();
        u.setAvatarUrl("http://localhost:8081/minio/scenary-media/avatar/7/a1b2.jpg");
        when(userMapper.findById(7L)).thenReturn(u);
        when(passwordEncoder.matches("Passw0rd!", "$2a$hash")).thenReturn(true);
        when(userMapper.deactivate(7L)).thenReturn(1);
        when(minio.objectKeyFromPublicUrl(u.getAvatarUrl())).thenReturn("avatar/7/a1b2.jpg");

        service.deactivate(7L, "Passw0rd!");

        verify(userMapper).deactivate(7L);
        verify(minio).remove("avatar/7/a1b2.jpg");
        verify(noteService).deactivateAuthorNotes(7L);
        verify(commentService).deactivateAuthorComments(7L);
    }

    @Test
    void deactivateRejectsAlreadyDeactivatedUser() {
        UserEntity u = activeUser();
        u.setStatus(2);
        when(userMapper.findById(7L)).thenReturn(u);

        BizException ex = assertThrows(BizException.class,
                () -> service.deactivate(7L, "Passw0rd!"));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(noteService, never()).deactivateAuthorNotes(anyLong());
    }

    @Test
    void publicProfileTreatsDeactivatedAsNotFound() {
        UserEntity u = activeUser();
        u.setStatus(2);
        when(userMapper.findById(7L)).thenReturn(u);

        UserService userService = new UserService(userMapper, minio, noteService);
        BizException ex = assertThrows(BizException.class, () -> userService.publicProfile(7L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(minio, never()).remove(anyString());
    }
}
