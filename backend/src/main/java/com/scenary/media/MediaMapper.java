package com.scenary.media;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MediaMapper {

    int insert(MediaEntity media);

    MediaEntity findById(@Param("id") Long id);

    /** 消费者回调：成功写缩略图与宽高；最终失败由消费者写入 status=2 与失败信息。E2 起 url 列存 key */
    int updateProcessResult(@Param("id") Long id,
                            @Param("status") int status,
                            @Param("thumbObjectKey") String thumbObjectKey,
                            @Param("width") Integer width,
                            @Param("height") Integer height);

    int updateVideoProcessResult(@Param("id") Long id,
                                 @Param("thumbObjectKey") String thumbObjectKey,
                                 @Param("width") Integer width,
                                 @Param("height") Integer height,
                                 @Param("durationMs") Long durationMs,
                                 @Param("playbackObjectKey") String playbackObjectKey,
                                 @Param("playbackLowObjectKey") String playbackLowObjectKey);

    /** 最终失败：写 status=2 并记录失败原因/时间，供轮询端立即展示失败态。 */
    int updateFailureResult(@Param("id") Long id,
                            @Param("failureReason") String failureReason,
                            @Param("failedAt") java.util.Date failedAt);

    int updateVideoFailureResult(@Param("id") Long id,
                                 @Param("failureReason") String failureReason,
                                 @Param("failedAt") java.util.Date failedAt);

    /** 抢占一次 MQ 发布机会，避免定时重试与首发并发重复抢占。 */
    int claimPublish(@Param("id") Long id, @Param("attemptedAt") java.util.Date attemptedAt);

    int markPublishSuccess(@Param("id") Long id);

    int markPublishFailure(@Param("id") Long id, @Param("reason") String reason);

    List<MediaEntity> selectPendingPublish(@Param("before") java.util.Date before,
                                           @Param("limit") int limit);

    /** 仅删除未绑定笔记的游离媒体行（note_id IS NULL 条件防误删已发布素材）；对象文件保留 */
    int deleteUnbound(@Param("id") Long id, @Param("userId") Long userId);

    /** 清理任务删除已确认完成对象清理的游离媒体行。 */
    int deleteStaleUnbound(@Param("id") Long id);

    /** 发布事务内回填绑定与序号 */
    int bindToNote(@Param("noteId") Long noteId,
                   @Param("orderNo") Integer orderNo,
                   @Param("mediaId") Long mediaId);

    /**
     * 编辑事务内重绑：目标媒体为游离或已绑定本笔记时允许（docs/02 §5.11 全量替换语义）。
     */
    int rebindToNote(@Param("noteId") Long noteId,
                     @Param("orderNo") Integer orderNo,
                     @Param("mediaId") Long mediaId);

    /** 编辑事务内解绑不在新集合中的旧媒体（note_id 置空回到游离态，可被删除闭环回收） */
    int unbindFromNoteExcept(@Param("noteId") Long noteId,
                             @Param("keepIds") List<Long> keepIds);

    /** 按 note 集合批量取媒体行（用于封面/详情聚合），结果按 note_id,order_no 升序 */
    List<MediaEntity> selectByNoteIds(@Param("noteIds") List<Long> noteIds);

    List<MediaEntity> selectStaleUnbound(@Param("before") java.util.Date before,
                                         @Param("limit") int limit);
}
