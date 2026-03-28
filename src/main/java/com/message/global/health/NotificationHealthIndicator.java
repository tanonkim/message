package com.message.global.health;

import com.message.domain.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationHealthIndicator implements HealthIndicator {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    public Health health() {
        try {
            long count = notificationLogRepository.count();
            return Health.up()
                    .withDetail("totalNotifications", count)
                    .build();
        }
        catch (Exception e) {
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
