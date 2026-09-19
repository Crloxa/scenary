package com.scenary.report;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportMapper {

    /** 唯一键兜底去重：同用户同目标重复举报返回 0 行（docs/02 §10.1） */
    @Insert("""
            <script>
            INSERT INTO reports (reporter_id, target_note_id, target_comment_id, reason_code, reason_text)
            VALUES (#{reporterId},
                    <choose><when test="targetType == 'note'">#{targetId}</when><otherwise>NULL</otherwise></choose>,
                    <choose><when test="targetType == 'comment'">#{targetId}</when><otherwise>NULL</otherwise></choose>,
                    #{reasonCode}, #{reasonText})
            </script>
            """)
    int insert(@Param("reporterId") long reporterId,
               @Param("targetType") String targetType,
               @Param("targetId") long targetId,
               @Param("reasonCode") String reasonCode,
               @Param("reasonText") String reasonText);
}
