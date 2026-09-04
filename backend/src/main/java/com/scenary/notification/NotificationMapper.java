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
}
