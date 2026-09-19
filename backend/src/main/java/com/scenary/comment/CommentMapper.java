package com.scenary.comment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper {

    int insert(CommentEntity comment);

    CommentEntity findById(@Param("id") long id);

    CommentEntity findByIdForUpdate(@Param("id") long id);

    List<CommentRow> selectPage(@Param("noteId") long noteId,
                                @Param("cursor") long cursor,
                                @Param("limit") int limit);

    int softDelete(@Param("id") long id, @Param("userId") long userId);

    /** 账号注销：作者名下全部未删评论软删，前端按既有占位渲染（docs/02 §3.8） */
    int softDeleteAllByAuthor(@Param("userId") long userId);
}
