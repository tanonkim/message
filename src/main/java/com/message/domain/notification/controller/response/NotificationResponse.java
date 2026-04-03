package com.message.domain.notification.controller.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.message.domain.notification.entity.NotificationLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "알림 발송 응답")
public record NotificationResponse(
        @Schema(description = "알림 로그 ID", example = "1")
        Long notificationLogId,
        @Schema(description = "발송 채널 (SMS, ALIMTALK, EMAIL, PUSH)", example = "SMS")
        String channel,
        @Schema(description = "발송 상태 (PENDING, SENT, FAILED)", example = "PENDING")
        String status,
        @Schema(description = "발송 완료 시각", example = "2026-04-03T15:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime sentAt,
        @Schema(description = "벤더 발송 메시지 ID", example = "MSG-20260403-001")
        String providerMessageId,
        @Schema(description = "폴백 채널 (폴백 발송된 경우)", example = "SMS")
        String fallbackChannel,
        @Schema(description = "발송 비용", example = "10.00")
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
