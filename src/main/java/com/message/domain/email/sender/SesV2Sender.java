package com.message.domain.email.sender;

import com.message.domain.email.command.EmailSendCommand;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Slf4j
@Component
public class SesV2Sender implements NotificationSender {

    private final SesV2Client sesV2Client;
    private final SesProperties sesProperties;
    private final StageEmailFilter stageEmailFilter;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public SesV2Sender(SesProperties sesProperties, StageEmailFilter stageEmailFilter) {
        this.sesProperties = sesProperties;
        this.stageEmailFilter = stageEmailFilter;
        this.sesV2Client = SesV2Client.builder()
                .region(Region.of(sesProperties.region()))
                .build();
    }

    // notification 도메인 경계 어댑터 — NotificationMessage → EmailSendCommand 변환
    @Override
    @CircuitBreaker(name = "ses", fallbackMethod = "fallback")
    public SendResult send(NotificationMessage message) {
        EmailSendCommand command = new EmailSendCommand(
                message.recipient(),
                message.content(),
                message.templateCode()
        );
        return doSend(command);
    }

    // 실제 발송 로직 — email 도메인의 Command만 사용
    private SendResult doSend(EmailSendCommand command) {
        String toEmail = command.recipient();

        // Stage 환경 발송 제한
        if ("stage".equals(activeProfile) && !stageEmailFilter.isAllowed(toEmail)) {
            log.warn("Stage email blocked (not in allowed domains): {}", toEmail);
            return SendResult.failure("Stage 환경에서 허용되지 않는 도메인입니다: " + toEmail);
        }

        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .fromEmailAddress(sesProperties.fromEmail())
                    .destination(Destination.builder().toAddresses(toEmail).build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder()
                                            .data(resolveSubject(command))
                                            .charset("UTF-8")
                                            .build())
                                    .body(Body.builder()
                                            .text(Content.builder()
                                                    .data(resolveBody(command))
                                                    .charset("UTF-8")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            SendEmailResponse response = sesV2Client.sendEmail(request);
            String messageId = response.messageId();
            log.info("SES email sent: messageId={}, to={}", messageId, toEmail);
            return SendResult.success(messageId, 0.1);

        } catch (SesV2Exception e) {
            log.error("SesV2Sender failed: to={}, code={}", toEmail, e.awsErrorDetails().errorCode(), e);
            return SendResult.failure(e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("SesV2Sender unexpected error: to={}", toEmail, e);
            return SendResult.failure(e.getMessage());
        }
    }

    private SendResult fallback(NotificationMessage message, Throwable e) {
        if (e instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            log.warn("이메일 Circuit Breaker OPEN - SES 호출 차단됨: {}", e.getMessage());
            return SendResult.failure("Circuit Breaker OPEN: 이메일 서비스 일시 중단");
        }
        log.error("SesV2Sender failed: to={}", message.recipient(), e);
        return SendResult.failure(e.getMessage());
    }

    private String resolveSubject(EmailSendCommand command) {
        return command.templateCode() != null ? "[알림] " + command.templateCode() : "[알림]";
    }

    private String resolveBody(EmailSendCommand command) {
        return command.content() != null ? command.content() : "";
    }
}