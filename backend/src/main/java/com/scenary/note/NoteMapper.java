package com.scenary.note;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteMapper {

    int insert(NoteEntity note);

    NoteEntity findById(@Param("id") Long id);

    /** 社交写事务内锁定目标笔记，避免检查公开状态后与软删竞态。 */
    NoteEntity findByIdForUpdate(@Param("id") Long id);

    NoteEntity findByUserAndRequestKey(@Param("userId") Long userId,
                                       @Param("requestKey") String requestKey);

    /**
     * feed 游标查询：visibility=1 AND id<cursor，order id DESC；吃 idx_feed_cursor。
     */
    List<NoteEntity> selectFeedRows(@Param("cursor") long cursor, @Param("limit") int limit);

    /**
     * 个人网格：showPrivate 仅本人视角（visibility IN (0,1)），否则仅公开。
     */
    List<NoteEntity> selectGridRows(@Param("userId") Long userId,
                                    @Param("cursor") long cursor,
                                    @Param("limit") int limit,
                                    @Param("showPrivate") boolean showPrivate);

    /** 软删：visibility=2 + deleted_at=now */
    int softDelete(@Param("id") Long id);

    /** 账号注销：作者名下全部可见笔记软删（docs/02 §3.8） */
    int softDeleteAllByAuthor(@Param("userId") long userId);
}
