package com.message.domain.notification.entity;

import com.message.domain.notification.enums.NotificationChannel;
import com.message.domain.notification.enums.NotificationPriority;
import com.message.domain.notification.enums.NotificationStatus;
import com.message.global.crypto.StringCryptoConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notification_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationLogId;

    @Column(unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority;

    @Convert(converter = StringCryptoConverter.class)
    @Column(nullable = false)
    private String recipient;

    private String templateId;

    @Column(columnDefinition = "TEXT")
    private String variables;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    private int retryCount;

    private String fallbackChannel;

    private String providerMessageId;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(precision = 10, scale = 4)
    private BigDecimal cost;

    @Column(nullable = false)
    private LocalDateTime createAt;

    private LocalDateTime sentAt;


    @Builder
    public NotificationLog(String idempotencyKey, String serviceId, NotificationChannel channel,
                           NotificationPriority priority, String recipient, String templateId,
                           String variables, String fallbackChannel) {
        this.idempotencyKey = idempotencyKey;
        this.serviceId = serviceId;
        this.channel = channel;
        this.priority = priority;
        this.recipient = recipient;
        this.templateId = templateId;
        this.variables = variables;
        this.fallbackChannel = fallbackChannel;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.cost = BigDecimal.ZERO;
        this.createAt = LocalDateTime.now();
    }

    public void markSent(String providerMessageId, BigDecimal cost) {
        this.status = NotificationStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.cost = cost;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    public void markFallback(String fallbackChannel) {
        this.status = NotificationStatus.FALLBACK;
        this.fallbackChannel = fallbackChannel;
    }

}
