package com.message.domain.notification.enum_type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationStatus {

    PENDING("대기"),        // 발송 대기
    SENT("발송 완료"),      // 발송 완료
    DELIVERED("전달 완료"), // 전달 완료
    FAILED("실패"),         // 발송 실패
    FALLBACK("폴백")        // 폴백 전환
    ;

    private final String desc;
}
