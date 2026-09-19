package com.scenary.user;

import jakarta.validation.constraints.NotBlank;

/** 注销账号入参：密码二次确认（docs/02 §3.8）。 */
public record DeactivateRequest(
        @NotBlank(message = "需要密码确认") String password) {
}
