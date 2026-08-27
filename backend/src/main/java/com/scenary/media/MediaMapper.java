package com.scenary.media;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
