package com.message.domain.notification.service.query.usecase;

import com.message.domain.notification.controller.response.NotificationResponse;

public interface NotificationQueryUseCase {
    NotificationResponse getStatus(Long notificationLogId);
}
