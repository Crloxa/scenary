package com.scenary.search;

import java.util.Date;

import lombok.Data;

/** 搜索查询投影，不把 note/user/media Entity 跨模块传播。 */
@Data
public class SearchRow {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;
    private Integer coverWidth;
    private Integer coverHeight;
    private Integer mediaCount;
    private String placeName;
    private Date createdAt;
    private String authorNickname;
    private String authorAvatarUrl;
    private Integer relevanceScore;
}
