package com.scenary.place;

import java.util.Date;

import lombok.Data;

/**
 * 地图视野框查询行（docs/05 §15 E5-1）：与 SearchRow 同构的最小卡片面，
 * 坐标 + 封面 + 作者昵称，供 marker/popup 渲染。
 */
@Data
public class PlaceNoteRow {
    private Long id;
    private Long userId;
    private String title;
    private String coverUrl;
    private Double latitude;
    private Double longitude;
    private String placeName;
    private Date createdAt;
    private String authorNickname;
}
