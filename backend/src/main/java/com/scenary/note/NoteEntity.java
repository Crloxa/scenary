package com.scenary.note;

import lombok.Data;

import java.util.Date;

/**
 * notes 表实体（模块私有，docs/01 §4.1）。
 */
@Data
public class NoteEntity {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String coverUrl;
    private Integer mediaCount;
    private String placeName;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private String placeSource;
    private String placePrecision;
    private String requestKey;
    /** 1公开 0私密 2已删除(软删) */
    private Integer visibility;
    /** P18 举报计数（V10），达阈值自动隐藏 visibility 1→3 */
    private Integer reportCount;
    private Date createdAt;
    private Date updatedAt;
}
