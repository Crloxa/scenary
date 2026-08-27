package com.scenary.note;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 发布入参。visibility 为 v2.3 契约补充字段（原表缺失私密创建入口）：缺省 1 公开，仅允许 0/1。
 */
public record NoteCreateRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 64, message = "标题最长 64 字") String title,
        @Size(max = 2000, message = "正文最长 2000 字") String content,
        @Size(max = 128, message = "地点名最长 128 字") String placeName,
        @NotEmpty(message = "至少选择一张图片") @Size(max = 9, message = "最多 9 张图片")
        List<Long> mediaIds,
        @Min(value = 0, message = "visibility 仅允许 0(私密)/1(公开)")
        @Max(value = 1, message = "visibility 仅允许 0(私密)/1(公开)") Integer visibility) {
}
