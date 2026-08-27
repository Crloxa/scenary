package com.scenary.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册入参，校验规则对应 docs/02 §2.1；密码字母+数字组成在 service 内二次校验以便给出明确文案。
 */
public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需 4~20 位字母/数字/下划线") String username,
        @NotBlank @Size(min = 8, max = 64, message = "密码长度 8~64") String password,
        @Size(min = 1, max = 32, message = "昵称长度 1~32") String nickname) {
}
