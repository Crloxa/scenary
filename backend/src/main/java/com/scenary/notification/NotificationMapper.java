package com.scenary.notification;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    int insert(@Param("recipientId") long recipientId,
               @Param("actorId") long actorId,
               @Param("type") String type,
               @Param("noteId") Long noteId,
               @Param("commentId") Long commentId);

    List<NotificationRow> selectPage(@Param("recipientId") long recipientId,
                                     @Param("cursor") long cursor,
                                     @Param("limit") int limit);

    long countUnread(@Param("recipientId") long recipientId);

    int markRead(@Param("recipientId") long recipientId,
                 @Param("ids") List<Long> ids);

    int markAllRead(@Param("recipientId") long recipientId);

    /** 保留策略统计：已读且早于 cutoff 的通知数（ops dry-run 用） */
    long countReadOlderThan(@Param("cutoff") long cutoffMillis);

    /** 保留策略清理：分批删除已读且早于 cutoff 的通知（docs/02 §5.9） */
    int deleteReadOlderThan(@Param("cutoff") long cutoffMillis, @Param("limit") int limit);
}
