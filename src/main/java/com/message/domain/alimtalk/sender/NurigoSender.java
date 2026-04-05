package com.message.domain.alimtalk.sender;

import com.message.domain.alimtalk.command.AlimtalkSendCommand;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
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

    // notification 도메인 경계 어댑터 — NotificationMessage → AlimtalkSendCommand 변환
    @Override
    public SendResult send(NotificationMessage message) {
        AlimtalkSendCommand command = new AlimtalkSendCommand(
                message.recipient(),
                message.templateCode(),
                message.variables()
        );
        return doSend(command);
    }

    // 실제 발송 로직 — alimtalk 도메인의 Command만 사용
    private SendResult doSend(AlimtalkSendCommand command) {
        try {
            Message kakaoMessage = new Message();
            kakaoMessage.setFrom(nurigoProperties.fromNumber());
            kakaoMessage.setTo(command.recipient());

            KakaoOption kakaoOption = new KakaoOption();
            kakaoOption.setPfId(null);
            kakaoOption.setTemplateId(command.templateCode());

            if (command.variables() != null) {
                Map<String, String> variables = new HashMap<>(command.variables());
                kakaoOption.setVariables(variables);
            }

            kakaoMessage.setKakaoOptions(kakaoOption);

            SingleMessageSentResponse response = messageService.sendOne(
                    new SingleMessageSendingRequest(kakaoMessage)
            );

            String messageId = response.getMessageId();
            log.info("Nurigo alimtalk sent: messageId={}, recipient={}", messageId, command.recipient());
            return SendResult.success(messageId, 8.0);
        } catch (Exception e) {
            log.error("NurigoSender failed: recipient={}", command.recipient(), e);
            return SendResult.failure(e.getMessage());
        }
    }
}
