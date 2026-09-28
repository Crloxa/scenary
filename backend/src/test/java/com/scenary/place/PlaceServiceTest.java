package com.scenary.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PlaceProvider provider;
    @Mock private PlaceNoteMapper placeNoteMapper;
    @Mock private com.scenary.note.NoteService noteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void stubRedis() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
    }

    private PlaceService service(boolean enabled) {
        PlaceProperties properties = new PlaceProperties();
        properties.setProviderEnabled(enabled);
        return new PlaceService(redis, new RateLimitService(redis), properties, provider, objectMapper,
                placeNoteMapper, noteService);
    }

    @Test
    void disabledReturnsEmptyCandidateWithoutProviderOrCache() {
        ReverseGeocodeVO vo = service(false).reverseGeocode(1L, 31.2304, 121.4737);
        assertNull(vo.placeName());
        assertEquals("none", vo.provider());
        assertFalse(vo.cached());
        verify(provider, never()).reverseGeocode(anyDouble(), anyDouble());
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        PlaceService svc = service(true);
        BizException lat = assertThrows(BizException.class,
                () -> svc.reverseGeocode(1L, 90.1, 0.0));
        assertEquals(ErrorCode.VALIDATION, lat.getErrorCode());
        BizException lng = assertThrows(BizException.class,
                () -> svc.reverseGeocode(1L, 0.0, -180.000001));
        assertEquals(ErrorCode.VALIDATION, lng.getErrorCode());
        BizException missing = assertThrows(BizException.class,
                () -> svc.reverseGeocode(1L, null, 0.0));
        assertEquals(ErrorCode.VALIDATION, missing.getErrorCode());
    }

    @Test
    void cacheHitSkipsProvider() throws Exception {
        when(valueOperations.get("place:rg:" + Geohash.encode(31.2304, 121.4737, 5)))
                .thenReturn(objectMapper.writeValueAsString(new JsonCacheValue("人民广场", "nominatim")));
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 31.2304, 121.4737);
        assertEquals("人民广场", vo.placeName());
        assertEquals("nominatim", vo.provider());
        assertTrue(vo.cached());
        verify(provider, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    void cacheMissQueriesProviderAndStoresForThirtyDays() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(provider.reverseGeocode(31.2304, 121.4737)).thenReturn(Optional.of("人民广场"));
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 31.2304, 121.4737);
        assertEquals("人民广场", vo.placeName());
        assertFalse(vo.cached());
        verify(valueOperations).set(anyString(), anyString(),
                eq(TimeUnit.DAYS.toSeconds(30)), eq(TimeUnit.SECONDS));
    }

    @Test
    void providerFailureYieldsEmptyCandidateAndNoCacheWrite() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(provider.id()).thenReturn("nominatim");
        when(provider.reverseGeocode(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("provider timeout simulation"));
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 5.0, 5.0);
        assertNull(vo.placeName());
        assertEquals("nominatim", vo.provider());
        assertFalse(vo.cached());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void providerEmptyResultIsReturnedButNotCached() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(provider.id()).thenReturn("nominatim");
        when(provider.reverseGeocode(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 0.0, 0.0);
        assertNull(vo.placeName());
        assertEquals("nominatim", vo.provider());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void rateLimitExceededThrows429() {
        when(valueOperations.increment(anyString())).thenReturn(31L);
        BizException ex = assertThrows(BizException.class,
                () -> service(true).reverseGeocode(1L, 31.2304, 121.4737));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        verify(provider, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    // ---------- E5 地图视野框浏览（docs/05 §15） ----------

    private PlaceNoteRow row(long id, java.util.Date createdAt) {
        PlaceNoteRow row = new PlaceNoteRow();
        row.setId(id);
        row.setUserId(7L);
        row.setTitle("标题" + id);
        row.setCoverUrl("cover/" + id + ".webp");
        row.setLatitude(30.9785);
        row.setLongitude(102.7591);
        row.setPlaceName("四姑娘山");
        row.setCreatedAt(createdAt);
        row.setAuthorNickname("山客");
        return row;
    }

    @Test
    void mapNotesRejectsInvalidBbox() {
        PlaceService svc = service(true);
        assertEquals(ErrorCode.VALIDATION, assertThrows(BizException.class,
                () -> svc.mapNotes(null, 31, 30, 100, 101, null, null)).getErrorCode());
        assertEquals(ErrorCode.VALIDATION, assertThrows(BizException.class,
                () -> svc.mapNotes(null, -91, 10, 100, 101, null, null)).getErrorCode());
        assertEquals(ErrorCode.VALIDATION, assertThrows(BizException.class,
                () -> svc.mapNotes(null, 0, 10, -181, 100, null, null)).getErrorCode());
        verify(placeNoteMapper, never()).selectNotesInBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), anyInt());
    }

    @Test
    void mapNotesMapsRowsAndSignsCoverViaNoteService() {
        PlaceNoteRow r = row(901, new java.util.Date(1_700_000_000_000L));
        when(placeNoteMapper.selectNotesInBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), anyInt())).thenReturn(List.of(r));
        when(noteService.viewUrl("cover/901.webp")).thenReturn("http://signed/901");

        PlaceMapPage page = service(true).mapNotes(null, 30, 31, 102, 103, null, null);

        assertEquals(1, page.list().size());
        assertEquals("http://signed/901", page.list().get(0).coverUrl());
        assertEquals(901L, page.list().get(0).id());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
    }

    @Test
    void mapNotesHasMoreEmitsOpaqueKeysetCursor() throws Exception {
        java.util.Date base = new java.util.Date(1_700_000_000_000L);
        when(placeNoteMapper.selectNotesInBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), anyInt()))
                .thenReturn(List.of(row(903, base), row(902, base), row(901, base)));
        when(noteService.viewUrl(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // limit=2 + 返回 3 行 → hasMore；下一页游标 = 第 2 行的 (createdAt,id)
        PlaceMapPage page = service(true).mapNotes(null, 30, 31, 102, 103, null, 2);

        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        MapCursor cursor = MapCursor.parse(page.nextCursor());
        assertEquals(1_700_000_000_000L, cursor.createdAt());
        assertEquals(902L, cursor.id());
        // 游标作为下一页输入回传 mapper（keyset 语义）；mapper 实际收到 pageSize+1（多取一条判 hasMore）
        service(true).mapNotes(null, 30, 31, 102, 103, page.nextCursor(), 2);
        verify(placeNoteMapper, org.mockito.Mockito.times(1))
                .selectNotesInBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                        eq(new java.util.Date(1_700_000_000_000L)), eq(902L), any(), eq(3));
    }

    @Test
    void mapNotesInvalidCursorThrows40000() {
        BizException ex = assertThrows(BizException.class,
                () -> service(true).mapNotes(null, 30, 31, 102, 103, "!!!not-base64!!!", null));
        assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
    }

    @Test
    void mapNotesClampsLimitToContractMax() {
        when(placeNoteMapper.selectNotesInBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), anyInt())).thenReturn(List.of());
        service(true).mapNotes(null, 30, 31, 102, 103, null, 999);
        // 契约上限 20（§1.3）；mapper 收到 pageSize+1 = 21（多取一条判 hasMore）
        verify(placeNoteMapper).selectNotesInBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), eq(21));
    }

    /** 与 PlaceService 内部缓存 JSON 同形，用于构造缓存值 */
    private record JsonCacheValue(String placeName, String provider) {
    }
}
