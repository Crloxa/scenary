package com.scenary.social;

import lombok.Data;

/** 关注关系列表行（docs/02 §3.9/§3.10，P16-02），id 为 follows.id 供游标分页，模块私有。 */
@Data
public class FollowRow {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Boolean following;
}
