package com.scenary.note;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Date;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;
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
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void stubRateLimitRedis() {
        // 限流器在每次 create 前查询 Redis；未打桩的 increment 返回 null 视为放行
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        // 详情/网格签名出口按恒等变换，便于断言透传值
        lenient().when(minio.viewUrl(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private NoteService service() {
        return new NoteService(noteMapper, mediaMapper, userMapper, minio, redis,
                new RateLimitService(redis));
    }

    @Test
    void createReturnsExistingNoteForDuplicateRequestKey() {
        NoteService service = service();

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
        // 幂等重放不触碰媒体/用户与缓存失效；coverUrl 仍需经 viewUrl 签名（minio 例外）
        verifyNoInteractions(mediaMapper, userMapper);
    }

    @Test
    void createReturnsExistingNoteAfterConcurrentUniqueKeyCollision() {
        NoteService service = service();

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
        // 幂等重放不触碰缓存失效与媒体摘要；redis 仅允许限流器计数
        verify(redis, never()).delete(anyString());
        verifyNoInteractions(userMapper);
    }

    private NoteEntity existingNote(long id) {
        NoteEntity existing = new NoteEntity();
        existing.setId(id);
        existing.setCoverUrl("http://example/cover.jpg");
        return existing;
    }

    @Test
    void createRollsBackWhenMediaBindDoesNotAffectRow() {
        NoteService service = service();

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
        NoteService service = service();

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
        NoteService service = service();

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
        NoteService service = service();

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

    @Test
    void updateRejectsSoftDeletedAndForeignNotes() {
        NoteService service = service();

        NoteEntity deleted = new NoteEntity();
        deleted.setId(9L);
        deleted.setUserId(7L);
        deleted.setVisibility(2);
        when(noteMapper.findByIdForUpdate(9L)).thenReturn(deleted);
        BizException gone = assertThrows(BizException.class, () -> service.update(7L, 9L,
                new NoteUpdateRequest("t", null, null, List.of(1L), null, null, null, null, 1)));
        assertEquals(ErrorCode.NOT_FOUND, gone.getErrorCode());

        NoteEntity foreign = new NoteEntity();
        foreign.setId(10L);
        foreign.setUserId(8L);
        foreign.setVisibility(1);
        when(noteMapper.findByIdForUpdate(10L)).thenReturn(foreign);
        BizException forbidden = assertThrows(BizException.class, () -> service.update(7L, 10L,
                new NoteUpdateRequest("t", null, null, List.of(1L), null, null, null, null, 1)));
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getErrorCode());
        verify(noteMapper, never()).update(any());
    }

    @Test
    void updateReplacesMediaAndInvalidatesCardCache() {
        NoteService service = service();

        NoteEntity note = new NoteEntity();
        note.setId(9L);
        note.setUserId(7L);
        note.setVisibility(1);
        when(noteMapper.findByIdForUpdate(9L)).thenReturn(note);

        MediaEntity kept = media(11L, 9L, 1, (byte) 0);
        MediaEntity fresh = media(12L, null, 1, (byte) 0);
        when(mediaMapper.findById(11L)).thenReturn(kept);
        when(mediaMapper.findById(12L)).thenReturn(fresh);
        when(mediaMapper.rebindToNote(9L, 1, 11L)).thenReturn(1);
        when(mediaMapper.rebindToNote(9L, 2, 12L)).thenReturn(1);

        NoteCreatedVO updated = service.update(7L, 9L,
                new NoteUpdateRequest("新标题", "正文", null, List.of(11L, 12L),
                        null, null, null, null, 1));

        assertEquals(9L, updated.id());
        verify(noteMapper).update(note);
        assertEquals("新标题", note.getTitle());
        assertEquals("thumb-11", note.getCoverUrl());
        assertEquals(2, note.getMediaCount());
        // 不在新集合的旧媒体被解绑；已绑定本笔记与游离媒体都保留
        verify(mediaMapper).unbindFromNoteExcept(9L, List.of());
        verify(redis).delete("note:card:9");
    }

    @Test
    void updateRejectsMediaBoundToAnotherNote() {
        NoteService service = service();

        NoteEntity note = new NoteEntity();
        note.setId(9L);
        note.setUserId(7L);
        note.setVisibility(1);
        when(noteMapper.findByIdForUpdate(9L)).thenReturn(note);
        when(mediaMapper.findById(13L)).thenReturn(media(13L, 77L, 1, (byte) 0));

        BizException ex = assertThrows(BizException.class, () -> service.update(7L, 9L,
                new NoteUpdateRequest("t", null, null, List.of(13L), null, null, null, null, 1)));
        assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
        verify(noteMapper, never()).update(any());
    }

    @Test
    void applyReportCountHidesNoteAtThresholdAndBumpsFeedVersion() {
        NoteService service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reportHideThreshold", 5);

        NoteEntity note = new NoteEntity();
        note.setId(9L);
        note.setUserId(8L);
        note.setVisibility(1);
        note.setReportCount(4);
        when(noteMapper.findById(9L)).thenReturn(note);
        when(noteMapper.selectReportCount(9L)).thenReturn(5);
        when(noteMapper.hideByReports(9L)).thenReturn(1);

        boolean hidden = service.applyReportCount(7L, 9L);

        assertTrue(hidden);
        verify(noteMapper).incrementReportCount(9L);
        verify(noteMapper).hideByReports(9L);
        verify(redis).delete("note:card:9");
        verify(redis).delete("feed:first:v1");
        verify(valueOperations).increment("feed:first:v1:version");
    }

    @Test
    void applyReportCountBelowThresholdKeepsVisible() {
        NoteService service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reportHideThreshold", 5);

        NoteEntity note = new NoteEntity();
        note.setId(10L);
        note.setUserId(8L);
        note.setVisibility(1);
        note.setReportCount(1);
        when(noteMapper.findById(10L)).thenReturn(note);
        when(noteMapper.selectReportCount(10L)).thenReturn(2);

        boolean hidden = service.applyReportCount(7L, 10L);

        assertFalse(hidden);
        verify(noteMapper, never()).hideByReports(anyLong());
    }

    private MediaEntity media(long id, Long noteId, int status, int mediaType) {
        MediaEntity m = new MediaEntity();
        m.setId(id);
        m.setUserId(7L);
        m.setNoteId(noteId);
        m.setStatus(status);
        m.setMediaType(mediaType);
        m.setThumbUrl("thumb-" + id);
        return m;
    }
}
