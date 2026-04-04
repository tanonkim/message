package com.message.domain.sms.sender;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolapiSender implements NotificationSender {

    private static final String API_URL = "https://api.solapi.com/messages/v4/send";
    private final SolapiProperties solapiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Override
    @CircuitBreaker(name = "solapi", fallbackMethod = "fallback")
    public SendResult send(NotificationMessage message) {
        try {
            String date = Instant.now().toString();
            String salt = UUID.randomUUID().toString().replace("-", "");
            String signature = generateSignature(date, salt);
            String authHeader = String.format("HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s",
                    solapiProperties.apiKey(), date, salt, signature);

            Map<String, Object> body = buildRequestBody(message);
            String response = restClient.post()
                    .uri(API_URL)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            return SendResult.success(extractMessageId(response), 8.0);
        }
        catch (Exception e) {
            log.error("SolapiSender failed: recipient={}", message.recipient(), e);
            return SendResult.failure(e.getMessage());
        }
    }

    SendResult fallback(NotificationMessage message, CallNotPermittedException e) {
        log.warn("SMS Circuit Breaker OPEN - Solapi 호출 차단됨: {}", e.getMessage());
        return SendResult.failure("Circuit Breaker OPEN: SMS 서비스 일시 중단");
    }


    private Map<String, Object> buildRequestBody(NotificationMessage message) {
        return Map.of("message", Map.of(
                "to", message.recipient(),
                "from", solapiProperties.fromNumber(),
                "text", message.content() != null ? message.content() : ""
        ));
    }

    private String generateSignature(String date, String salt) throws NoSuchAlgorithmException, InvalidKeyException {
        String data = date + salt;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(solapiProperties.apiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(String response) {
        try {
            Map<String, Object> map = objectMapper.readValue(response, Map.class);
            return String.valueOf(map.getOrDefault("messageId", "unknown"));
        } catch (Exception e) {
            return "unknown";
        }
    }
}
