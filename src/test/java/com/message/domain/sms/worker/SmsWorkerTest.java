package com.message.domain.sms.worker;

import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender;
import com.message.domain.notification.service.NotificationLogCommandService;
import com.message.domain.sms.sender.SolapiSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsWorkerTest {

    @Mock private JmsTemplate jmsTemplate;
    @Mock private NotificationLogCommandService notificationLogCommandService;
    @Mock private SolapiSender solapiSender;

    private SmsWorker smsWorker;

    @BeforeEach
    void setUp() {
        smsWorker = new SmsWorker(jmsTemplate, notificationLogCommandService, solapiSender);
    }

    @Test
    @DisplayName("SMS 발송 성공 시 CommandService.markSent 호출")
    void processCritical_성공시_markSent_호출() {
        NotificationMessage message = new NotificationMessage(
                1L, "SMS", "CRITICAL", "01012345678",
                "sms-template-01", null, "인증번호는 123456입니다.", null, null, 0
        );
        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.success("provider-msg-001", 10.0));

        smsWorker.processCritical(message);

        verify(notificationLogCommandService).markSent(eq(1L), eq("provider-msg-001"), eq(BigDecimal.valueOf(10.0)), eq("SMS"));
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + retryCount < maxRetry + delayMs > 0 이면 지연 재시도 큐 발행 (HIGH 우선순위)")
    void processHigh_실패_재시도가능_지연재시도_큐발행() {
        NotificationMessage message = new NotificationMessage(
                2L, "SMS", "HIGH", "01012345678",
                null, null, "주문이 완료되었습니다.", null, null, 0
        );
        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("vendor timeout"));

        smsWorker.processHigh(message);

        verify(jmsTemplate).convertAndSend(eq("noti.high.sms"), any(NotificationMessage.class), any());
        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.sms"), any(Object.class));
        verify(notificationLogCommandService, never()).markSent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SMS 발송 실패 + retryCount < maxRetry + delayMs = 0 이면 즉시 재시도 큐 발행 (CRITICAL 우선순위)")
    void processCritical_실패_재시도가능_즉시재시도_큐발행() {
        NotificationMessage message = new NotificationMessage(
                3L, "SMS", "CRITICAL", "01099999999",
                null, null, "인증번호가 발급되었습니다.", null, null, 0
        );
        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("connection refused"));

        smsWorker.processCritical(message);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(jmsTemplate).convertAndSend(eq("noti.critical.sms"), captor.capture());
        assertThat(captor.getValue().retryCount()).isEqualTo(1);
        assertThat(captor.getValue().channel()).isEqualTo("SMS");

        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.sms"), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + 재시도 소진 + fallback 설정 시 fallback 큐로 재발행 및 markFallback 호출")
    void processNormal_실패_재시도소진_fallback_큐발행() {
        NotificationMessage message = new NotificationMessage(
                4L, "SMS", "NORMAL", "01088888888",
                null, null, "배송이 시작되었습니다.", "EMAIL", "배송 시작 안내 이메일 본문", 3
        );
        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("invalid number"));

        smsWorker.processNormal(message);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(jmsTemplate).convertAndSend(eq("noti.normal.email"), captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo("EMAIL");
        assertThat(captor.getValue().content()).isEqualTo("배송 시작 안내 이메일 본문");
        assertThat(captor.getValue().retryCount()).isEqualTo(0);

        verify(notificationLogCommandService).markFallback(eq(4L), eq("EMAIL"));
        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.sms"), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + 재시도 소진 + fallback 없으면 DLQ 적재 및 markFailed 호출")
    void processNormal_실패_재시도소진_fallback없음_dlq적재() {
        NotificationMessage message = new NotificationMessage(
                5L, "SMS", "NORMAL", "01077777777",
                null, null, "알림 내용", null, null, 3
        );
        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("forbidden by carrier"));

        smsWorker.processNormal(message);

        verify(jmsTemplate).convertAndSend(eq("noti.dlq.sms"), any(NotificationMessage.class));
        verify(notificationLogCommandService).markFailed(eq(5L), eq("forbidden by carrier"), eq("SMS"));
    }
}
