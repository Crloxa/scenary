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
    private String requestKey;
    /** 1公开 0私密 2已删除(软删) */
    private Integer visibility;
    private Date createdAt;
    private Date updatedAt;
}
