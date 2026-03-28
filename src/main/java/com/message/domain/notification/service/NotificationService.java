package com.message.domain.notification.service;

import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SaveNotificationService saveNotificationService;

    public NotificationResponse request(NotificationRequest request) {
        return saveNotificationService.request(request);
    }
}
