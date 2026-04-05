package com.message.domain.notification.service;

import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.global.metrics.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationLogCommandService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationMetrics notificationMetrics;

    public void markSent(Long logId, String providerMessageId, BigDecimal cost, String channel) {
        NotificationLog log = findOrThrow(logId);
        log.markSent(providerMessageId, cost);
        notificationMetrics.recordSent(channel);
    }

    public void markFallback(Long logId, String fallbackChannel) {
        NotificationLog log = findOrThrow(logId);
        log.markFallback(fallbackChannel);
    }

    public void markFailed(Long logId, String errorMessage, String channel) {
        NotificationLog log = findOrThrow(logId);
        log.markFailed(errorMessage);
        notificationMetrics.recordFailed(channel);
        notificationMetrics.recordDlq(channel);
    }

    private NotificationLog findOrThrow(Long logId) {
        return notificationLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalStateException("NotificationLog not found: " + logId));
    }
}
