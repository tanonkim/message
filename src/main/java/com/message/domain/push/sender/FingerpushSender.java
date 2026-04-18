package com.message.domain.push.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
import com.message.domain.push.command.PushSendCommand;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FingerpushSender implements NotificationSender {

    private static final String API_BASE_URL = "https://api.fingerpush.com";
    private static final int BATCH_SIZE = 500;

    private final FingerpushProperties fingerpushProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    // notification 도메인 경계 어댑터 — NotificationMessage → PushSendCommand 변환
    @Override
    @CircuitBreaker(name = "fingerpush", fallbackMethod = "fallback")
    public SendResult send(NotificationMessage message) {
        PushSendCommand command = new PushSendCommand(
                message.recipient(),
                message.content()
        );
        return doSend(command);
    }

    // 실제 발송 로직 — push 도메인의 Command만 사용 (테스트를 위해 package-private)
    SendResult doSend(PushSendCommand command) {
        String recipient = command.recipient();
        String content = command.content() != null ? command.content() : "";

        if (recipient == null || recipient.isBlank()) {
            return sendEntire(content);
        } else if (recipient.contains(",")) {
            List<String> tokens = Arrays.stream(recipient.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            return sendTarget(tokens, content);
        } else {
            return sendSingle(recipient, content);
        }
    }

    private SendResult fallback(NotificationMessage message, Throwable e) {
        if (e instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            log.warn("푸시 Circuit Breaker OPEN - Fingerpush 호출 차단됨: {}", e.getMessage());
            return SendResult.failure("Circuit Breaker OPEN: FingerPush 서비스 일시 중단");
        }
        log.error("FingerpushSender failed: recipient={}", message.recipient(), e);
        return SendResult.failure(e.getMessage());
    }

    private SendResult sendSingle(String deviceToken, String content) {
        Map<String, Object> body = Map.of(
                "app_id", fingerpushProperties.appId(),
                "device_token", deviceToken,
                "message", content
        );
        postToFingerpush("/push/single", body);
        log.info("Fingerpush SINGLE sent: device={}", deviceToken);
        return SendResult.success("single-" + deviceToken, 0);
    }

    private SendResult sendTarget(List<String> tokens, String content) {
        List<List<String>> batches = partition(tokens, BATCH_SIZE);
        String lastId = null;
        int failCount = 0;

        for (List<String> batch : batches) {
            try {
                Map<String, Object> body = Map.of(
                        "app_id", fingerpushProperties.appId(),
                        "device_tokens", batch,
                        "message", content
                );
                lastId = postToFingerpush("/push/target", body);
            } catch (Exception e) {
                log.error("Fingerpush batch failed: size={}, error={}", batch.size(), e.getMessage());
                failCount += batch.size();
            }
        }

        log.info("Fingerpush TARGET: total={}, failed={}", tokens.size(), failCount);

        if (failCount == tokens.size()) {
            throw new RuntimeException("Fingerpush 전체 배치 발송 실패");
        }
        return SendResult.success("target-" + (lastId != null ? lastId : "unknown"), 0);
    }

    private SendResult sendEntire(String content) {
        Map<String, Object> body = Map.of(
                "app_id", fingerpushProperties.appId(),
                "message", content
        );
        String messageId = postToFingerpush("/push/entire", body);
        log.info("Fingerpush ENTIRE sent");
        return SendResult.success("entire-" + messageId, 0);
    }

    private String postToFingerpush(String path, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(API_BASE_URL + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + fingerpushProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Fingerpush 요청 직렬화 실패", e);
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}