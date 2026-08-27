package com.scenary.media;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MediaMapper {

    int insert(MediaEntity media);

    MediaEntity findById(@Param("id") Long id);

    /** 消费者回调：成功写缩略图与宽高；失败置 status=2 由人工经 DLQ 排查后处理 */
    int updateProcessResult(@Param("id") Long id,
                            @Param("status") int status,
                            @Param("thumbObjectKey") String thumbObjectKey,
                            @Param("thumbUrl") String thumbUrl,
                            @Param("width") Integer width,
                            @Param("height") Integer height);

    /** 仅删除未绑定笔记的游离媒体行（note_id IS NULL 条件防误删已发布素材）；对象文件保留 */
    int deleteUnbound(@Param("id") Long id, @Param("userId") Long userId);

    /** 发布事务内回填绑定与序号 */
    int bindToNote(@Param("noteId") Long noteId,
                   @Param("orderNo") Integer orderNo,
                   @Param("mediaId") Long mediaId);

    /** 按 note 集合批量取媒体行（用于封面/详情聚合），结果按 note_id,order_no 升序 */
    List<MediaEntity> selectByNoteIds(@Param("noteIds") List<Long> noteIds);
}
