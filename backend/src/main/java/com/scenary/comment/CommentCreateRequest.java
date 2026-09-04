package com.scenary.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 评论/回复请求；回复的 300 字限制由服务端按 parentId 再收紧。 */
public record CommentCreateRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论最长 500 字") String content,
        Long parentId) {
}
