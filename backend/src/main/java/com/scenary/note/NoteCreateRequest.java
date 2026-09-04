package com.scenary.note;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
        @jakarta.validation.constraints.DecimalMin(value = "-90.0", message = "纬度范围为 -90~90")
        @jakarta.validation.constraints.DecimalMax(value = "90.0", message = "纬度范围为 -90~90")
        BigDecimal latitude,
        @jakarta.validation.constraints.DecimalMin(value = "-180.0", message = "经度范围为 -180~180")
        @jakarta.validation.constraints.DecimalMax(value = "180.0", message = "经度范围为 -180~180")
        BigDecimal longitude,
        @Size(max = 16, message = "地点来源最长 16 字") String placeSource,
        @Size(max = 32, message = "地点精度最长 32 字") String placePrecision,
        @Min(value = 0, message = "visibility 仅允许 0(私密)/1(公开)")
        @Max(value = 1, message = "visibility 仅允许 0(私密)/1(公开)") Integer visibility,
        @Size(max = 64, message = "幂等键最长 64 字") String requestKey) {

    /** 兼容未带地点坐标的旧调用方。 */
    public NoteCreateRequest(String title, String content, String placeName,
                             List<Long> mediaIds, Integer visibility, String requestKey) {
        this(title, content, placeName, mediaIds, null, null, null, null, visibility, requestKey);
    }
}
