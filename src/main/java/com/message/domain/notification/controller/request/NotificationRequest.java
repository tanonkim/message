package com.message.domain.notification.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record NotificationRequest(
        @NotBlank String serviceId,
        @NotBlank String channel,
        @NotBlank String priority,
        @NotNull @Valid RecipientDto recipient,
        TemplateDto template,
        String content,
        FallbackDto fallback,
        String idempotencyKey,
        String scheduledAt
) {
    public record RecipientDto(String phone, String email) {
        public String primary() {
            return phone != null ? phone : email;
        }
    }

    public record TemplateDto(String id, Map<String, String> variables) {
    }

    public record FallbackDto(String channel, String content) {
    }
}
