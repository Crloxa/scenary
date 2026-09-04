package com.scenary.notification;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ids 为空数组表示全部标记已读。 */
public record NotificationReadRequest(
        @NotNull(message = "ids 不能为空")
        @Size(max = 100, message = "一次最多标记 100 条通知") List<Long> ids) {
}
