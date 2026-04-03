package com.message.global.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier {

    private final SlackProperties slackProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public void sendDlqAlert(String queueName, Long notificationLogId, String errorMessage) {
        if (!slackProperties.isEnabled()) {
            log.warn("[DLQ Alert] queue={}, logId={}, error={}", queueName, notificationLogId, errorMessage);
            return;
        }

        try {
            String text = String.format(
                    ":rotating_light: *DLQ 적재 알림*\n" +
                            "큐: `%s`\n" +
                            "발송ID: `%d`\n" +
                            "오류: %s",
                    queueName, notificationLogId, errorMessage
            );

            String body = objectMapper.writeValueAsString(Map.of("text", text));
            restClient.post()
                    .uri(slackProperties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Slack DLQ alert sent: queue={}", queueName);
        } catch (Exception e) {
            log.error("Failed to send Slack alert", e);
        }
    }
}
