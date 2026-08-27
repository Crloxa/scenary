package com.scenary.user;

import java.util.Date;

import lombok.Data;

/**
 * users 表 ORM 实体，仅限本模块与跨模块 Service 门面间接使用（docs/01 §4.1 依赖铁律）。
 */
@Data
public class UserEntity {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String bio;
    /** 1正常 0禁用 */
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
