package com.scenary.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 举报入参（docs/02 §10.1，P18）：targetType 区分笔记/评论；
 * reasonCode 受控枚举，自由文本仅作补充证据，≤200 字纯文本。
 */
public record ReportRequest(
        @NotBlank(message = "目标类型不能为空")
        @Pattern(regexp = "note|comment", message = "目标类型仅允许 note/comment")
        String targetType,
        @jakarta.validation.constraints.Positive(message = "目标 id 必须为正整数")
        long targetId,
        @NotBlank(message = "举报原因不能为空")
        @Pattern(regexp = "SPAM|PORTRAIT|INFRINGING|OTHER", message = "举报原因无效")
        String reasonCode,
        @Size(max = 200, message = "补充说明最长 200 字") String reasonText) {
}
