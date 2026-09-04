package com.scenary.notification;

import java.util.Date;

import lombok.Data;

/** 通知查询行，关联内容删除后仍保留事件。 */
@Data
public class NotificationRow {

    private Long id;
    private Long recipientId;
    private Long actorId;
    private String type;
    private Long noteId;
    private Long commentId;
    private Date readAt;
    private Date createdAt;
    private String actorNickname;
    private String actorAvatarUrl;
    private String noteTitle;
    private String commentContent;
    private Integer commentStatus;
}
