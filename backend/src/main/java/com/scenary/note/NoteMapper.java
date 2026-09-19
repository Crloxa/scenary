package com.scenary.note;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoteMapper {

    int insert(NoteEntity note);

    /** 编辑（docs/02 §5.11）：全字段内容更新，updated_at 由 SQL 兜底刷新 */
    int update(NoteEntity note);

    NoteEntity findById(@Param("id") Long id);

    /** 社交写事务内锁定目标笔记，避免检查公开状态后与软删竞态。 */
    NoteEntity findByIdForUpdate(@Param("id") Long id);

    NoteEntity findByUserAndRequestKey(@Param("userId") Long userId,
                                       @Param("requestKey") String requestKey);

    /**
     * feed 游标查询：visibility=1 AND id<cursor，order id DESC；吃 idx_feed_cursor。
     * viewerId 非空时叠加屏蔽双向过滤（P18）。
     */
    List<NoteEntity> selectFeedRows(@Param("viewerId") Long viewerId,
                                    @Param("cursor") long cursor, @Param("limit") int limit);

    /**
     * 个人网格：showPrivate 仅本人视角（visibility IN (0,1)），否则仅公开。
     */
    List<NoteEntity> selectGridRows(@Param("userId") Long userId,
                                    @Param("viewerId") Long viewerId,
                                    @Param("cursor") long cursor,
                                    @Param("limit") int limit,
                                    @Param("showPrivate") boolean showPrivate);

    /** 软删：visibility=2 + deleted_at=now */
    int softDelete(@Param("id") Long id);

    /** 账号注销：作者名下全部可见笔记软删（docs/02 §3.8） */
    int softDeleteAllByAuthor(@Param("userId") long userId);

    /** P18 举报计数（V10 notes.report_count） */
    int incrementReportCount(@Param("noteId") long noteId);

    Integer selectReportCount(@Param("noteId") long noteId);

    /** P18 达阈值自动隐藏：visibility 1→3（审核隐藏态，仅作者可见，docs/05 §14） */
    int hideByReports(@Param("noteId") long noteId);
}
