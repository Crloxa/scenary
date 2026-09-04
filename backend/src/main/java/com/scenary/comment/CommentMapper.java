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
}
