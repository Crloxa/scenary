package com.scenary.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.UserContext;
import com.scenary.common.Result;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<NotificationPage> page(@RequestParam(required = false) Long cursor,
                                         @RequestParam(required = false) Integer limit) {
        return Result.ok(notificationService.page(UserContext.require(), cursor, limit));
    }

    @PostMapping("/read")
    public Result<Void> markRead(@Valid @RequestBody NotificationReadRequest request) {
        notificationService.markRead(UserContext.require(), request);
        return Result.ok();
    }
}
