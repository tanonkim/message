package com.message.domain.sms.worker;

import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.domain.notification.sender.NotificationSender;
import com.message.domain.sms.sender.SolapiSender;
import com.message.global.metrics.NotificationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsWorkerTest {

    @Mock private JmsTemplate jmsTemplate;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationMetrics notificationMetrics;
    @Mock private SolapiSender solapiSender;

    private SmsWorker smsWorker;

    @BeforeEach
    void setUp() {
        smsWorker = new SmsWorker(jmsTemplate, notificationLogRepository, notificationMetrics, solapiSender);
    }

    @Test
    @DisplayName("SMS 발송 성공 시 markSent 호출 및 메트릭 recordSent 기록")
    void processCritical_성공시_markSent_recordSent_호출() {
        // CRITICAL priority: maxRetry=5, delayMs=0
        NotificationMessage message = new NotificationMessage(
                1L, "SMS", "CRITICAL", "01012345678",
                "sms-template-01", null, "인증번호는 123456입니다.", null, null, 0
        );
        NotificationLog log = mock(NotificationLog.class);

        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.success("provider-msg-001", 10.0));
        given(notificationLogRepository.findById(1L)).willReturn(Optional.of(log));

        smsWorker.processCritical(message);

        verify(log).markSent(eq("provider-msg-001"), any());
        verify(notificationMetrics).recordSent("SMS");
        verify(jmsTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + retryCount < maxRetry + delayMs > 0 이면 지연 재시도 큐 발행 (HIGH 우선순위)")
    void processHigh_실패_재시도가능_지연재시도_큐발행() {
        // HIGH priority: maxRetry=3, delayMs=60000, retryCount=0 → 재시도 가능, 지연 발행
        NotificationMessage message = new NotificationMessage(
                2L, "SMS", "HIGH", "01012345678",
                null, null, "주문이 완료되었습니다.", null, null, 0
        );
        NotificationLog log = mock(NotificationLog.class);

        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("vendor timeout"));
        given(notificationLogRepository.findById(2L)).willReturn(Optional.of(log));

        smsWorker.processHigh(message);

        // 지연 발행: convertAndSend(queue, message, messagePostProcessor) — 3인자 형태
        verify(jmsTemplate).convertAndSend(eq("noti.high.sms"), any(NotificationMessage.class), any());
        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.sms"), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + retryCount < maxRetry + delayMs = 0 이면 즉시 재시도 큐 발행 (CRITICAL 우선순위)")
    void processCritical_실패_재시도가능_즉시재시도_큐발행() {
        // CRITICAL priority: maxRetry=5, delayMs=0, retryCount=0 → 재시도 가능, 즉시 발행
        NotificationMessage message = new NotificationMessage(
                3L, "SMS", "CRITICAL", "01099999999",
                null, null, "인증번호가 발급되었습니다.", null, null, 0
        );
        NotificationLog log = mock(NotificationLog.class);

        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("connection refused"));
        given(notificationLogRepository.findById(3L)).willReturn(Optional.of(log));

        smsWorker.processCritical(message);

        // 즉시 발행: convertAndSend(queue, message) — 2인자 형태
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(jmsTemplate).convertAndSend(eq("noti.critical.sms"), captor.capture());
        assertThat(captor.getValue().retryCount()).isEqualTo(1);
        assertThat(captor.getValue().channel()).isEqualTo("SMS");

        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.sms"), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + 재시도 소진 + fallback SMS 설정 시 fallback SMS 큐로 재발행")
    void processNormal_실패_재시도소진_fallback_sms_큐발행() {
        // NORMAL priority: maxRetry=3, retryCount=3 → 소진, fallback=EMAIL
        NotificationMessage message = new NotificationMessage(
                4L, "SMS", "NORMAL", "01088888888",
                null, null, "배송이 시작되었습니다.", "EMAIL", "배송 시작 안내 이메일 본문", 3
        );
        NotificationLog log = mock(NotificationLog.class);

        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("invalid number"));
        given(notificationLogRepository.findById(4L)).willReturn(Optional.of(log));

        smsWorker.processNormal(message);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(jmsTemplate).convertAndSend(eq("noti.normal.email"), captor.capture());

        NotificationMessage fallbackMessage = captor.getValue();
        assertThat(fallbackMessage.channel()).isEqualTo("EMAIL");
        assertThat(fallbackMessage.content()).isEqualTo("배송 시작 안내 이메일 본문");
        assertThat(fallbackMessage.retryCount()).isEqualTo(0);
        assertThat(fallbackMessage.fallbackChannel()).isNull();

        verify(log).markFallback("EMAIL");
        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.sms"), any(Object.class));
    }

    @Test
    @DisplayName("SMS 발송 실패 + 재시도 소진 + fallback 없으면 DLQ 적재 및 markFailed 호출")
    void processNormal_실패_재시도소진_fallback없음_dlq적재() {
        // NORMAL priority: maxRetry=3, retryCount=3 → 소진, fallback=null
        NotificationMessage message = new NotificationMessage(
                5L, "SMS", "NORMAL", "01077777777",
                null, null, "알림 내용", null, null, 3
        );
        NotificationLog log = mock(NotificationLog.class);

        given(solapiSender.send(any())).willReturn(NotificationSender.SendResult.failure("forbidden by carrier"));
        given(notificationLogRepository.findById(5L)).willReturn(Optional.of(log));

        smsWorker.processNormal(message);

        verify(jmsTemplate).convertAndSend(eq("noti.dlq.sms"), any(NotificationMessage.class));
        verify(log).markFailed("forbidden by carrier");
        verify(notificationMetrics).recordFailed("SMS");
        verify(notificationMetrics).recordDlq("SMS");
    }
}