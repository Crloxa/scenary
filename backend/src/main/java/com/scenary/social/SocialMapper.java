package com.scenary.social;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SocialMapper {

    int insertLike(@Param("noteId") long noteId, @Param("userId") long userId);

    int deleteLike(@Param("noteId") long noteId, @Param("userId") long userId);

    int insertBookmark(@Param("noteId") long noteId, @Param("userId") long userId);

    int deleteBookmark(@Param("noteId") long noteId, @Param("userId") long userId);

    int insertFollow(@Param("followerId") long followerId, @Param("followingId") long followingId);

    int deleteFollow(@Param("followerId") long followerId, @Param("followingId") long followingId);

    List<SocialRow> selectNoteSocial(@Param("noteIds") List<Long> noteIds,
                                     @Param("viewerId") Long viewerId);

    SocialRow selectUserSocial(@Param("targetUserId") long targetUserId,
                               @Param("viewerId") Long viewerId);

    List<BookmarkRow> selectBookmarks(@Param("userId") long userId,
                                      @Param("cursor") long cursor,
                                      @Param("limit") int limit);

    /** 关注者列表（docs/02 §3.9，P16-02）：游标 follows.id 倒序，仅 status=1 用户 */
    List<FollowRow> selectFollowers(@Param("userId") long userId,
                                    @Param("viewerId") Long viewerId,
                                    @Param("cursor") long cursor,
                                    @Param("limit") int limit);

    /** 正在关注列表（docs/02 §3.10，P16-02）：语义与 selectFollowers 对称 */
    List<FollowRow> selectFollowing(@Param("userId") long userId,
                                    @Param("viewerId") Long viewerId,
                                    @Param("cursor") long cursor,
                                    @Param("limit") int limit);
}
