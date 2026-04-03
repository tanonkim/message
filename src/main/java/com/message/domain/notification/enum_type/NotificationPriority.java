package com.message.domain.notification.enum_type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationPriority {

    CRITICAL(5, 0L, "긴급"),       // 긴급
    HIGH(3, 60_000L, "높음"),      // 높음
    NORMAL(3, 300_000L, "보통"),   // 보통 (5분 딜레이)
    LOW(1, 0L, "낮음")             // 낮음
    ;

    private final int maxRetry;
    private final long retryDelayMs;
    private final String desc;
}
