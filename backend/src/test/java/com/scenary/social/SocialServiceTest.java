package com.scenary.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.SocialVO;
import com.scenary.note.NoteService;
import com.scenary.common.PageResult;
import com.scenary.common.AuthorVO;
import com.scenary.user.UserService;

@ExtendWith(MockitoExtension.class)
class SocialServiceTest {

    @Mock
    private SocialMapper socialMapper;
    @Mock
    private NoteService noteService;
    @Mock
    private UserService userService;

    @Test
    void repeatedLikeIsIdempotentAndReturnsCurrentCounts() {
        SocialService service = new SocialService(socialMapper, noteService, userService);
        when(noteService.socialTarget(9L)).thenReturn(new NoteService.NoteTarget(9L, 11L));
        when(socialMapper.selectNoteSocial(List.of(9L), 7L)).thenReturn(List.of(row(true, false, 1, 2)));

        SocialVO first = service.like(7L, 9L, true);
        SocialVO second = service.like(7L, 9L, true);

        assertTrue(first.liked());
        assertEquals(1, second.likeCount());
        verify(socialMapper, times(2)).insertLike(9L, 7L);
        verify(noteService, times(2)).socialTarget(9L);
    }

    @Test
    void repeatedUnlikeIsSuccessfulEvenWhenRelationIsMissing() {
        SocialService service = new SocialService(socialMapper, noteService, userService);
        when(noteService.socialTarget(9L)).thenReturn(new NoteService.NoteTarget(9L, 11L));
        when(socialMapper.selectNoteSocial(List.of(9L), 7L)).thenReturn(List.of(row(false, false, 0, 0)));

        SocialVO result = service.like(7L, 9L, false);

        assertEquals(0, result.likeCount());
        verify(socialMapper).deleteLike(9L, 7L);
    }

    @Test
    void selfFollowIsRejectedBeforeWritingRelation() {
        SocialService service = new SocialService(socialMapper, noteService, userService);

        BizException ex = assertThrows(BizException.class, () -> service.follow(7L, 7L, true));

        assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
        verify(socialMapper, never()).insertFollow(7L, 7L);
    }

    @Test
    void followReturnsFollowerCountAndState() {
        SocialService service = new SocialService(socialMapper, noteService, userService);
        when(socialMapper.selectUserSocial(11L, 7L)).thenReturn(userRow(true, 3, 4));

        SocialVO result = service.follow(7L, 11L, true);

        assertTrue(result.following());
        assertEquals(3, result.followerCount());
        verify(socialMapper).insertFollow(7L, 11L);
    }

    @Test
    void bookmarkPageUsesBookmarkCursorAndEnrichesCurrentState() {
        SocialService service = new SocialService(socialMapper, noteService, userService);
        BookmarkRow bookmark = new BookmarkRow();
        bookmark.setBookmarkId(30L);
        bookmark.setNoteId(9L);
        bookmark.setTitle("收藏的风景");
        bookmark.setContent("海边的风");
        bookmark.setCoverUrl("/cover.jpg");
        bookmark.setMediaCount(1);
        bookmark.setAuthorId(11L);
        bookmark.setAuthorNickname("海风");
        bookmark.setCreatedAt(new Date(100L));
        when(socialMapper.selectBookmarks(7L, Long.MAX_VALUE, 11)).thenReturn(List.of(bookmark));
        when(socialMapper.selectNoteSocial(List.of(9L), 7L)).thenReturn(List.of(row(false, true, 2, 1)));

        PageResult<BookmarkVO> page = service.bookmarks(7L, null, null);

        assertEquals(1, page.getList().size());
        assertTrue(page.getList().get(0).social().bookmarked());
        assertEquals("海风", page.getList().get(0).author().nickname());
        verify(socialMapper).selectBookmarks(7L, Long.MAX_VALUE, 11);
    }

    private SocialRow row(boolean liked, boolean bookmarked, long likes, long bookmarks) {
        SocialRow row = new SocialRow();
        row.setNoteId(9L);
        row.setLiked(liked);
        row.setBookmarked(bookmarked);
        row.setFollowing(false);
        row.setLikeCount(likes);
        row.setBookmarkCount(bookmarks);
        row.setFollowerCount(3L);
        row.setFollowingCount(4L);
        return row;
    }

    private SocialRow userRow(boolean following, long followers, long followingCount) {
        SocialRow row = new SocialRow();
        row.setFollowing(following);
        row.setFollowerCount(followers);
        row.setFollowingCount(followingCount);
        return row;
    }
}
