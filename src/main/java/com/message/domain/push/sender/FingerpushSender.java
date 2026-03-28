package com.message.domain.push.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
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
    private final RestClient restClient = RestClient.create();

    @Override
    public SendResult send(NotificationMessage message) {
        try {
            String recipient = message.recipient();
            String content = message.content() != null ? message.content() : "";

            if (recipient == null || recipient.isBlank()) {
                return sendEntire(content);
            } else if (recipient.contains(",")) {
                return sendTarget(List.of(recipient.split(",")), content);
            } else {
                return sendSingle(recipient, content);
            }
        } catch (Exception e) {
            log.error("FingerpushSender failed", e);
            return SendResult.failure(e.getMessage());
        }
    }

    private SendResult sendSingle(String deviceToken, String content) throws Exception {
        Map<String, Object> body = Map.of(
                "app_id", fingerpushProperties.appId(),
                "device_token", deviceToken,
                "message", content
        );

        String response = postToFingerpush("/push/single", body);
        log.info("Fingerpush SINGLE sent: device={}", deviceToken);
        return SendResult.success("single-" + deviceToken, 0);
    }

    private SendResult sendTarget(List<String> tokens, String content) throws Exception {
        List<List<String>> batches = partition(tokens, BATCH_SIZE);
        String lastId = null;

        for (List<String> batch : batches) {
            Map<String, Object> body = Map.of(
                    "app_id", fingerpushProperties.appId(),
                    "device_tokens", batch,
                    "message", content
            );
            lastId = postToFingerpush("/push/target", body);
        }

        log.info("Fingerpush TARGET sent: count={}", tokens.size());
        return SendResult.success("target-" + lastId, 0);
    }

    private SendResult sendEntire(String content) throws Exception {
        Map<String, Object> body = Map.of(
                "app_id", fingerpushProperties.appId(),
                "message", content
        );

        String messageId = postToFingerpush("/push/entire", body);
        log.info("Fingerpush ENTIRE sent");
        return SendResult.success("entire-" + messageId, 0);
    }

    private String postToFingerpush(String path, Map<String, Object> body) throws Exception {
        String response = restClient.post()
                .uri(API_BASE_URL + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fingerpushProperties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);
        return response;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
