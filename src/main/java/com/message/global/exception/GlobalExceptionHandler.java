package com.message.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        log.warn("ApiException: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(ErrorCode.SEND_FAILED));
    }
}
