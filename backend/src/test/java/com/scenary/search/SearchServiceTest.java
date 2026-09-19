package com.scenary.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scenary.common.BizException;
import com.scenary.common.SocialVO;
import com.scenary.social.SocialService;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchMapper searchMapper;
    @Mock
    private SocialService socialService;

    @Test
    void relevancePageEscapesLikeCharactersAndBuildsPlainHighlight() {
        SearchRow row = row(9L, "A_% 远方", "带着 A_% 出发", "海岸 A_%", "旅人", 9, 2_000L);
        when(searchMapper.selectNotes("A\\_\\%", "relevance", null, null, null, 42L, 11))
                .thenReturn(List.of(row));
        when(socialService.noteStatuses(List.of(9L), 42L))
                .thenReturn(Map.of(9L, new SocialVO(true, false, false, 1, 0, 2, 3)));

        SearchPageResult<SearchNoteVO> page = new SearchService(searchMapper, socialService, null)
                .search(42L, "  A_%  ", null, null, "RELEVANCE");

        assertEquals(1, page.list().size());
        assertEquals("A_% 远方", page.list().get(0).highlight().title());
        assertEquals("带着 A_% 出发", page.list().get(0).highlight().content());
        assertEquals("海岸 A_%", page.list().get(0).highlight().placeName());
        assertEquals(null, page.list().get(0).highlight().author());
        assertTrue(page.list().get(0).social().liked());
        assertEquals(null, page.nextCursor());
    }

    @Test
    void cursorIsBoundToQueryAndSortAndCarriesTheLastOrderingKey() {
        SearchRow first = row(9L, "云海", "", "", "山客", 5, 2_000L);
        SearchRow probe = row(8L, "云海日出", "", "", "山客", 4, 1_000L);
        when(searchMapper.selectNotes("云海", "relevance", null, null, null, null, 2))
                .thenReturn(List.of(first, probe));

        SearchService service = new SearchService(searchMapper, socialService, null);
        SearchPageResult<SearchNoteVO> page = service.search(null, "云海", null, 1, "relevance");

        assertTrue(page.hasMore());
        SearchCursor cursor = SearchCursor.parse(page.nextCursor(), "云海", "relevance");
        assertEquals(5, cursor.score());
        assertEquals(2_000L, cursor.createdAt());
        assertEquals(9L, cursor.id());
        assertThrows(BizException.class,
                () -> SearchCursor.parse(page.nextCursor(), "海边", "relevance"));
        verify(searchMapper).selectNotes(eq("云海"), eq("relevance"), isNull(), isNull(), isNull(), isNull(), eq(2));
    }

    @Test
    void invalidQueryAndSortAreRejectedBeforeDatabaseAccess() {
        SearchService service = new SearchService(searchMapper, socialService, null);

        assertThrows(BizException.class, () -> service.search(null, " ", null, null, "recent"));
        assertThrows(BizException.class, () -> service.search(null, "a", null, null, "recent"));
        assertThrows(BizException.class, () -> service.search(null, "山海", null, null, "popular"));
        assertThrows(BizException.class, () -> service.search(null, "山海", " ", null, "recent"));
    }

    private SearchRow row(long id, String title, String content, String place,
                          String author, int score, long createdAt) {
        SearchRow row = new SearchRow();
        row.setId(id);
        row.setUserId(7L);
        row.setTitle(title);
        row.setContent(content);
        row.setPlaceName(place);
        row.setAuthorNickname(author);
        row.setAuthorAvatarUrl(null);
        row.setCoverUrl("/cover.jpg");
        row.setMediaCount(1);
        row.setRelevanceScore(score);
        row.setCreatedAt(new Date(createdAt));
        return row;
    }
}
