package com.scenary.note;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Date;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.media.MediaEntity;
import com.scenary.media.MediaMapper;
import com.scenary.media.MinioService;
import com.scenary.user.UserMapper;
import com.scenary.user.UserEntity;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteMapper noteMapper;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MinioService minio;
    @Mock
    private StringRedisTemplate redis;

    @Test
    void createReturnsExistingNoteForDuplicateRequestKey() {
        NoteService service = new NoteService(noteMapper, mediaMapper, userMapper, minio, redis);

        NoteEntity existing = new NoteEntity();
        existing.setId(42L);
        existing.setCoverUrl("http://example/cover.jpg");
        when(noteMapper.findByUserAndRequestKey(7L, "idem-1")).thenReturn(existing);

        NoteCreatedVO created = service.create(7L,
                new NoteCreateRequest("标题", null, null, List.of(1L), 1, "idem-1"));

        assertEquals(42L, created.id());
        assertEquals("http://example/cover.jpg", created.coverUrl());
        verify(noteMapper).findByUserAndRequestKey(7L, "idem-1");
        verify(noteMapper, never()).insert(any());
        verifyNoInteractions(mediaMapper, userMapper, minio);
    }

    @Test
    void createReturnsExistingNoteAfterConcurrentUniqueKeyCollision() {
        NoteService service = new NoteService(noteMapper, mediaMapper, userMapper, minio, redis);

        MediaEntity media = new MediaEntity();
        media.setId(11L);
        media.setUserId(7L);
        media.setStatus(1);
        media.setThumbUrl("http://example/thumb.jpg");
        when(noteMapper.findByUserAndRequestKey(7L, "idem-2"))
                .thenReturn(null)
                .thenReturn(existingNote(43L));
        when(mediaMapper.findById(11L)).thenReturn(media);
        when(noteMapper.insert(any(NoteEntity.class)))
                .thenThrow(new DuplicateKeyException("uk_note_user_request_key"));

        NoteCreatedVO created = service.create(7L,
                new NoteCreateRequest("标题", null, null, List.of(11L), 1, "idem-2"));

        assertEquals(43L, created.id());
        verify(noteMapper, times(2)).findByUserAndRequestKey(7L, "idem-2");
        verifyNoInteractions(userMapper, minio, redis);
    }

    private NoteEntity existingNote(long id) {
        NoteEntity existing = new NoteEntity();
        existing.setId(id);
        existing.setCoverUrl("http://example/cover.jpg");
        return existing;
    }

    @Test
    void createRollsBackWhenMediaBindDoesNotAffectRow() {
        NoteService service = new NoteService(noteMapper, mediaMapper, userMapper, minio, redis);

        MediaEntity media = new MediaEntity();
        media.setId(11L);
        media.setUserId(7L);
        media.setNoteId(null);
        media.setStatus(1);
        media.setThumbUrl("http://example/thumb.jpg");
        when(mediaMapper.findById(11L)).thenReturn(media);
        doAnswer(invocation -> {
            NoteEntity note = invocation.getArgument(0);
            note.setId(88L);
            return 1;
        }).when(noteMapper).insert(any(NoteEntity.class));
        when(mediaMapper.bindToNote(88L, 1, 11L)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> service.create(7L, new NoteCreateRequest("标题", null, null, List.of(11L), 1, null)));

        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
        verify(noteMapper).insert(any(NoteEntity.class));
        verify(mediaMapper).bindToNote(88L, 1, 11L);
    }

    @Test
    void detailUsesValueEqualityForOwnerViewerId() {
        NoteService service = new NoteService(noteMapper, mediaMapper, userMapper, minio, redis);

        NoteEntity note = new NoteEntity();
        note.setId(901L);
        note.setUserId(143L);
        note.setTitle("私密底稿");
        note.setContent("");
        note.setVisibility(0);
        note.setCreatedAt(new Date(123L));

        UserEntity author = new UserEntity();
        author.setId(143L);
        author.setNickname("山野行人");

        MediaEntity media = new MediaEntity();
        media.setId(501L);
        media.setThumbUrl("http://example/thumb.jpg");
        media.setWidth(800);
        media.setHeight(600);

        when(noteMapper.findById(901L)).thenReturn(note);
        when(userMapper.selectBriefs(List.of(143L))).thenReturn(List.of(author));
        when(mediaMapper.selectByNoteIds(List.of(901L))).thenReturn(List.of(media));
        when(minio.displayUrl(media)).thenReturn("http://example/thumb.jpg");

        NoteDetailVO detail = service.detail(Long.valueOf(143L), 901L);

        assertTrue(detail.mine());
        assertEquals(1, detail.images().size());
        assertEquals("http://example/thumb.jpg", detail.images().get(0).thumbUrl());
    }

    @Test
    void createPersistsExplicitMapCoordinates() {
        NoteService service = new NoteService(noteMapper, mediaMapper, userMapper, minio, redis);

        MediaEntity media = new MediaEntity();
        media.setId(21L);
        media.setUserId(7L);
        media.setStatus(1);
        media.setThumbUrl("http://example/thumb.jpg");
        when(mediaMapper.findById(21L)).thenReturn(media);
        doAnswer(invocation -> {
            NoteEntity note = invocation.getArgument(0);
            note.setId(99L);
            return 1;
        }).when(noteMapper).insert(any(NoteEntity.class));
        when(mediaMapper.bindToNote(99L, 1, 21L)).thenReturn(1);

        service.create(7L, new NoteCreateRequest("山谷", "", "四姑娘山", List.of(21L),
                new BigDecimal("30.9785"), new BigDecimal("102.7591"), "map", "EXACT", 1, null));

        org.mockito.ArgumentCaptor<NoteEntity> noteCaptor =
                org.mockito.ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteMapper).insert(noteCaptor.capture());
        NoteEntity saved = noteCaptor.getValue();
        assertEquals(new BigDecimal("30.9785"), saved.getLatitude());
        assertEquals(new BigDecimal("102.7591"), saved.getLongitude());
        assertEquals("MAP", saved.getPlaceSource());
        assertEquals("EXACT", saved.getPlacePrecision());
    }

    @Test
    void derivesExifCoordinatesButDetailDoesNotExposeThem() {
        NoteService service = new NoteService(noteMapper, mediaMapper, userMapper, minio, redis);

        MediaEntity media = new MediaEntity();
        media.setId(22L);
        media.setUserId(7L);
        media.setStatus(1);
        media.setThumbUrl("http://example/thumb.jpg");
        media.setExifLatitude(new BigDecimal("30.123456"));
        media.setExifLongitude(new BigDecimal("102.654321"));
        when(mediaMapper.findById(22L)).thenReturn(media);
        doAnswer(invocation -> {
            NoteEntity note = invocation.getArgument(0);
            note.setId(100L);
            note.setCreatedAt(new Date(456L));
            return 1;
        }).when(noteMapper).insert(any(NoteEntity.class));
        when(mediaMapper.bindToNote(100L, 1, 22L)).thenReturn(1);

        service.create(7L, new NoteCreateRequest("EXIF", "", "", List.of(22L),
                null, null, "EXIF", null, 1, null));

        org.mockito.ArgumentCaptor<NoteEntity> noteCaptor =
                org.mockito.ArgumentCaptor.forClass(NoteEntity.class);
        verify(noteMapper).insert(noteCaptor.capture());
        NoteEntity saved = noteCaptor.getValue();
        assertEquals("EXIF", saved.getPlaceSource());
        assertEquals(new BigDecimal("30.123456"), saved.getLatitude());
        assertEquals(new BigDecimal("102.654321"), saved.getLongitude());
    }
}
