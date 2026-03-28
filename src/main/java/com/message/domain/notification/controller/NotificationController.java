package com.message.domain.notification.controller;

import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse send(@RequestBody @Valid NotificationRequest notificationRequest) {
        return notificationService.request(notificationRequest);
    }

    @GetMapping("/{id}")
    public NotificationResponse getStatus(@PathVariable Long id) {
        return notificationService.getStatus(id);
    }
}
