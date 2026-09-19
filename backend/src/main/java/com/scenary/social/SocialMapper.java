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
    /** P18 屏蔽（docs/02 §10.2）：唯一键幂等，自屏蔽由 CHECK 约束兜底 */
    int insertBlock(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);

    int deleteBlock(@Param("userId") long userId, @Param("blockedUserId") long blockedUserId);

    /** 双向屏蔽判定：a、b 任一方屏蔽对方即 true（feed/搜索/网格/详情过滤共用） */
    Boolean existsBlockEitherWay(@Param("userA") long userA, @Param("userB") long userB);

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
