package com.scenary.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /** 账号注销：status=2 注销态 + 昵称匿名化 + 清空 bio/头像（docs/02 §3.8）；用户名保留占用 */
    int deactivate(@Param("id") long id);

    /** 笔记计数：本人视角含私密(0,1)，他人视角仅公开=1；均已删(2)恒不计 */
    long countNotes(@Param("userId") Long userId, @Param("includePrivate") boolean includePrivate);

    /** 批量取 id/昵称/头像 摘要（feed 卡片与笔记详情共用） */
    List<UserEntity> selectBriefs(@Param("ids") List<Long> ids);
}
