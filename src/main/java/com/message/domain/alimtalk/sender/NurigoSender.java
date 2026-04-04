package com.message.domain.alimtalk.sender;

import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.KakaoOption;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class NurigoSender implements NotificationSender {

    private final DefaultMessageService messageService;
    private final NurigoProperties nurigoProperties;

    public NurigoSender(NurigoProperties nurigoProperties) {
        this.nurigoProperties = nurigoProperties;
        this.messageService = NurigoApp.INSTANCE.initialize(
                nurigoProperties.apiKey(),
                nurigoProperties.apiSecret(),
                "https://api.coolsms.co.kr"
        );
    }

    @Override
    @CircuitBreaker(name = "nurigo", fallbackMethod = "fallback")
    public SendResult send(NotificationMessage message) {
        try {
            Message kakaoMessage = new Message();
            kakaoMessage.setFrom(nurigoProperties.fromNumber());
            kakaoMessage.setTo(message.recipient());

            KakaoOption kakaoOption = new KakaoOption();
            kakaoOption.setPfId(null); // pfId는 템플릿에서 관리
            kakaoOption.setTemplateId(message.templateCode());

            if (message.variables() != null) {
                Map<String, String> variables = new HashMap<>(message.variables());
                kakaoOption.setVariables(variables);
            }

            kakaoMessage.setKakaoOptions(kakaoOption);

            SingleMessageSentResponse response = messageService.sendOne(
                    new SingleMessageSendingRequest(kakaoMessage)
            );

            String messageId = response.getMessageId();
            log.info("Nurigo alimtalk sent: messageId={}, recipient={}", messageId, message.recipient());
            return SendResult.success(messageId, 8.0);
        }
        catch (Exception e) {
            log.error("NurigoSender failed: recipient={}", message.recipient(), e);
            return SendResult.failure(e.getMessage());
        }
    }

    SendResult fallback(NotificationMessage message, CallNotPermittedException e) {
        log.warn("Nurigo Circuit Breaker OPEN - Nurigo 호출 차단됨: {}", e.getMessage());
        return SendResult.failure("Circuit Breaker OPEN: Nurigo 서비스 일시 중단");
    }
}
