package com.message.domain.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationChannel {

    SMS("SMS 문자"),        // SMS 문자
    ALIMTALK("알림톡"),     // 카카오 알림톡
    EMAIL("이메일"),        // 이메일
    PUSH("앱 푸시")         // 앱 푸시
    ;

    private final String desc;
}