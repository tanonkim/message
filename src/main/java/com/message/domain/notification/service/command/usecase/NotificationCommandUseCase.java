package com.message.domain.notification.service.command.usecase;

import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;

public interface NotificationCommandUseCase {
    NotificationResponse request(NotificationRequest request);
}
