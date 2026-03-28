package com.message.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.blocklist.service.BlockService;
import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.enums.NotificationChannel;
import com.message.domain.notification.enums.NotificationPriority;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.repository.DetailNotificaionLogRepository;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.global.exception.ApiException;
import com.message.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveNotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final DetailNotificaionLogRepository detailNotificaionLogRepository;
    private final BlockService blockService;
    private final ObjectMapper objectMapper;
    private final JmsTemplate jmsTemplate;

    public NotificationResponse request(NotificationRequest request) {
        String idempotencyKey = resolveIdempotencyKey(request); // 중복 방지 키

        // 중복 요청 체크
        if (detailNotificaionLogRepository.existsByIdempotencyKey(idempotencyKey)) {
            detailNotificaionLogRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ApiException(ErrorCode.DUPLICATE_REQUEST));

            log.warn("Duplicate request detected: idempotencyKey={}", idempotencyKey);
            throw new ApiException(ErrorCode.DUPLICATE_REQUEST);
        }

        // 수신 차단 Pass
        if (blockService.isBlocked(request)) {
            log.warn("Blocked recipient: serviceId={}", request.serviceId());
            NotificationLog blockedLog = saveLog(request, idempotencyKey);
            blockedLog.markFailed("수신 차단된 대상입니다");
            notificationLogRepository.save(blockedLog);
            return NotificationResponse.from(blockedLog);
        }

        // 채널/우선순위 검증
        NotificationChannel channel = parseChannel(request.channel());
        NotificationPriority priority = parsePriority(request.priority());

        // 발송 이력 저장 (PENDING)
        NotificationLog log = saveLog(request, idempotencyKey);

        // JMS 큐 발행
        NotificationMessage message = buildMessage(log.getNotificationLogId(), request, channel, priority);
        String queueName = message.queueName();

        jmsTemplate.convertAndSend(queueName, message);

        return NotificationResponse.pending(log.getNotificationLogId());
    }

    private NotificationMessage buildMessage(Long logId, NotificationRequest request, NotificationChannel channel, NotificationPriority priority) {
        return new NotificationMessage(
                logId,
                channel.name(),
                priority.name(),
                request.recipient().primary(),
                request.template() != null ? request.template().id() : null,
                request.template() != null ? request.template().variables() : null,
                request.content(),
                request.fallback() != null ? request.fallback().channel() : null,
                request.fallback() != null ? request.fallback().content() : null,
                0
        );
    }

    private NotificationLog saveLog(NotificationRequest request, String idempotencyKey) {
        String variables = null;
        if (request.template() != null && request.template().variables() != null) {
            try {
                variables = objectMapper.writeValueAsString(request.template().variables());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize template variables", e);
            }
        }

        NotificationLog notificationLog = NotificationLog.builder()
                .idempotencyKey(idempotencyKey)
                .serviceId(request.serviceId())
                .channel(parseChannel(request.channel()))
                .priority(parsePriority(request.priority()))
                .recipient(request.recipient().primary())
                .templateId(request.template() != null ? request.template().id() : null)
                .variables(variables)
                .fallbackChannel(request.fallback() != null ? request.fallback().channel() : null)
                .build();

        return notificationLogRepository.save(notificationLog);
    }

    private NotificationChannel parseChannel(String channel) {
        try {
            return NotificationChannel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_CHANNEL, "지원하지 않는 채널입니다: " + channel);
        }
    }

    private NotificationPriority parsePriority(String priority) {
        try {
            return NotificationPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_PRIORITY, "지원하지 않는 우선순위입니다: " + priority);
        }
    }


    private String resolveIdempotencyKey(NotificationRequest request) {
        return request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID().toString();
    }

}
