package com.scenary.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.common.AuthorVO;
import com.scenary.common.PageResult;
import com.scenary.media.MediaMapper;
import com.scenary.note.NoteEntity;
import com.scenary.note.NoteMapper;
import com.scenary.note.NoteService;
import com.scenary.user.UserMapper;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private NoteMapper noteMapper;
    @Mock
    private NoteService noteService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void firstPageMissBuildsAndWritesVersionedCacheThenHitReadsIt() throws Exception {
        FeedService service = new FeedService(noteMapper, noteService, userMapper, mediaMapper, redis);
        NoteEntity note = note(10L);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(NoteService.KEY_FEED_FIRST_VERSION)).thenReturn("3");
        when(valueOperations.get(NoteService.KEY_FEED_FIRST + ":3")).thenReturn(null);
        when(noteMapper.selectFeedRows(Long.MAX_VALUE, 11)).thenReturn(List.of(note));
        when(noteService.briefsMap(List.of(7L))).thenReturn(Map.of());
        when(mediaMapper.selectByNoteIds(List.of(10L))).thenReturn(List.of());

        PageResult<NoteCardVO> first = service.page(null, null);

        assertEquals(List.of(10L), first.getList().stream().map(NoteCardVO::id).toList());
        assertFalse(first.isHasMore());
        verify(valueOperations).set(eq(NoteService.KEY_FEED_FIRST + ":3"), anyString(), eq(java.time.Duration.ofSeconds(300)));

        String cached = new ObjectMapper().writeValueAsString(PageResult.of(
                List.of(new NoteCardVO(10L, "晨雾", "", "/thumb.jpg", null, null, 1,
                        new AuthorVO(7L, "晴山", null), 100L)), null, false));
        when(valueOperations.get(NoteService.KEY_FEED_FIRST + ":3")).thenReturn(cached);
        PageResult<NoteCardVO> second = service.page(null, null);

        assertEquals(10L, second.getList().get(0).id());
        verify(noteMapper, org.mockito.Mockito.times(2)).selectFeedRows(Long.MAX_VALUE, 11);
    }

    @Test
    void cursorPageUsesCardCacheWithoutJoiningAuthorsOrMedia() throws Exception {
        FeedService service = new FeedService(noteMapper, noteService, userMapper, mediaMapper, redis);
        NoteCardVO card = new NoteCardVO(9L, "缓存卡片", "摘要", "/thumb.jpg", 300, 200, 1,
                new AuthorVO(7L, "晴山", null), 100L);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(NoteService.KEY_NOTE_CARD + "9"))
                .thenReturn(new ObjectMapper().writeValueAsString(card));
        NoteEntity row = note(9L);
        when(noteMapper.selectFeedRows(100L, 11)).thenReturn(List.of(row));

        PageResult<NoteCardVO> result = service.page(100L, 10);

        assertEquals(9L, result.getList().get(0).id());
        verify(noteService, never()).briefsMap(org.mockito.ArgumentMatchers.anyList());
        verify(mediaMapper, never()).selectByNoteIds(org.mockito.ArgumentMatchers.anyList());
    }

    private NoteEntity note(long id) {
        NoteEntity note = new NoteEntity();
        note.setId(id);
        note.setUserId(7L);
        note.setTitle("晨雾");
        note.setContent("清晨的山谷");
        note.setCoverUrl("/thumb.jpg");
        note.setMediaCount(1);
        note.setVisibility(1);
        note.setCreatedAt(new Date(100L));
        return note;
    }
}
