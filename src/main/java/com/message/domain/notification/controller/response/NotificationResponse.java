package com.message.domain.notification.controller.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.message.domain.notification.entity.NotificationLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationLogId,
        String channel,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime sentAt,
        String providerMessageId,
        String fallbackChannel,
        BigDecimal cost
) {
    public static NotificationResponse pending(Long id) {
        return new NotificationResponse(id, null, "PENDING", null, null, null, null);
    }

    public static NotificationResponse from(NotificationLog log) {
        return new NotificationResponse(
                log.getId(),
                log.getChannel().name(),
                log.getStatus().name(),
                log.getSentAt(),
                log.getProviderMessageId(),
                log.getFallbackChannel(),
                log.getCost()
        );
    }
}
