package com.scenary.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * users 表数据访问。SQL 一律写在同.resources/mapper/UserMapper.xml，接口只声明意图。
 */
@Mapper
public interface UserMapper {

    int insert(UserEntity user);

    UserEntity findByUsername(@Param("username") String username);

    UserEntity findById(@Param("id") Long id);

    /** 资料编辑：nickname/bio 可选字段，null 不更新 */
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("bio") String bio);

    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);
}
