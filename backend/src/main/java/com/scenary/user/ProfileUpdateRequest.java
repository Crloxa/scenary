package com.scenary.user;

import jakarta.validation.constraints.Size;

/**
 * 资料编辑（docs/02 §3.2）：两字段均可选，"至少一项"由 service 校验。
 */
public record ProfileUpdateRequest(
        @Size(min = 1, max = 32, message = "昵称长度 1~32") String nickname,
        @Size(max = 200, message = "签名最长 200 字") String bio) {
}
