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
}
