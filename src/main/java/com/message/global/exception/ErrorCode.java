package com.message.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_CHANNEL(HttpStatus.BAD_REQUEST, "INVALID_CHANNEL", "지원하지 않는 채널입니다"),
    INVALID_PRIORITY(HttpStatus.BAD_REQUEST, "INVALID_PRIORITY", "지원하지 않는 우선순위입니다"),
    MISSING_RECIPIENT(HttpStatus.BAD_REQUEST, "MISSING_RECIPIENT", "수신자 정보가 없습니다"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "API Key가 유효하지 않습니다"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", "이미 처리된 요청입니다"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "발송 이력을 찾을 수 없습니다"),
    SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SEND_FAILED", "발송 처리 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
