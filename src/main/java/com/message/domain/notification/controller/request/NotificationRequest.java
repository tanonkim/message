package com.message.domain.notification.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Schema(description = "알림 발송 요청")
public record NotificationRequest(
        @Schema(description = "서비스 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "order-service")
        @NotBlank String serviceId,
        @Schema(description = "발송 채널 (SMS, ALIMTALK, EMAIL, PUSH)", requiredMode = Schema.RequiredMode.REQUIRED, example = "SMS")
        @NotBlank String channel,
        @Schema(description = "우선순위 (CRITICAL, HIGH, NORMAL, LOW)", requiredMode = Schema.RequiredMode.REQUIRED, example = "NORMAL")
        @NotBlank String priority,
        @Schema(description = "수신자 정보", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Valid RecipientDto recipient,
        @Schema(description = "템플릿 정보 (template 또는 content 중 하나 필수)")
        TemplateDto template,
        @Schema(description = "발송 내용 (template 또는 content 중 하나 필수)", example = "주문이 완료되었습니다.")
        String content,
        @Schema(description = "폴백 채널 설정 (발송 실패 시 대체 채널)")
        FallbackDto fallback,
        @Schema(description = "멱등성 키 (중복 발송 방지, 미입력 시 자동 생성)", example = "order-12345-sms")
        String idempotencyKey,
        @Schema(description = "예약 발송 시각 (ISO 8601, 미입력 시 즉시 발송)", example = "2026-04-03T15:00:00")
        String scheduledAt
) {
    @Schema(description = "수신자 정보")
    public record RecipientDto(
            @Schema(description = "휴대폰 번호 (SMS, ALIMTALK, PUSH 채널 시 필수)", example = "01012345678")
            String phone,
            @Schema(description = "이메일 주소 (EMAIL 채널 시 필수)", example = "user@example.com")
            String email
    ) {
        public String primary() {
            return phone != null ? phone : email;
        }
    }

    @Schema(description = "템플릿 정보")
    public record TemplateDto(
            @Schema(description = "템플릿 ID", example = "tmpl-001")
            String id,
            @Schema(description = "템플릿 변수 치환 값")
            Map<String, String> variables
    ) {
    }

    @Schema(description = "폴백 채널 정보")
    public record FallbackDto(
            @Schema(description = "폴백 채널 (SMS, ALIMTALK, EMAIL, PUSH)", example = "SMS")
            String channel,
            @Schema(description = "폴백 발송 내용", example = "주문이 완료되었습니다.")
            String content
    ) {
    }
}
