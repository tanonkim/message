package com.message.domain.notification.controller;

import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.service.command.usecase.NotificationCommandUseCase;
import com.message.domain.notification.service.query.usecase.NotificationQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "알림 발송", description = "알림 발송 및 상태 조회 API")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationCommandUseCase notificationCommandUseCase;
    private final NotificationQueryUseCase notificationQueryUseCase;

    @Operation(summary = "알림 발송 요청", description = "SMS, 카카오 알림톡, 이메일, 푸시 알림 발송을 요청합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "발송 요청 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            @ApiResponse(responseCode = "409", description = "중복 요청 (idempotency key 충돌)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse send(@RequestBody @Valid NotificationRequest notificationRequest) {
        return notificationCommandUseCase.request(notificationRequest);
    }

    @Operation(summary = "알림 발송 상태 조회", description = "알림 로그 ID로 발송 상태를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "알림 로그를 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public NotificationResponse getStatus(
            @Parameter(description = "알림 로그 ID", required = true) @PathVariable Long id) {
        return notificationQueryUseCase.getStatus(id);
    }
}