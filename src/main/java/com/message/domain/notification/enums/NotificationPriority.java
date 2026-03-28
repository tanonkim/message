package com.message.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationPriority {

    CRITICAL(5, 0L),
    High(3, 60_000L),
    NORMAL(3, 300_000L),   // 5분
    LOW(1, 0L);

    private final int maxRetry;
    private final long retryDelayMs;
}
