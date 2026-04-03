package com.message.domain.alimtalk.worker.worker;

import com.message.domain.alimtalk.sender.NurigoSender;
import com.message.domain.alimtalk.worker.AlimtalkWorker;
import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.domain.notification.sender.NotificationSender;
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
class AlimtalkWorkerTest {

    @Mock private JmsTemplate jmsTemplate;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationMetrics notificationMetrics;
    @Mock private NurigoSender nurigoSender;

    private AlimtalkWorker alimtalkWorker;

    @BeforeEach
    void setUp() {
        alimtalkWorker = new AlimtalkWorker(jmsTemplate, notificationLogRepository, notificationMetrics, nurigoSender);
    }

    @Test
    @DisplayName("알림톡 재시도 소진 + Fallback SMS 설정 시 SMS 큐로 재발행")
    void processHigh_fallback_to_sms() {
        // HIGH maxRetry=3, retryCount=3 → 소진, fallback=SMS
        NotificationMessage message = new NotificationMessage(
                1L, "ALIMTALK", "HIGH", "01012345678",
                "template-01", null, null, "SMS", "SMS 대체 내용", 3
        );
        NotificationLog log = mock(NotificationLog.class);

        given(nurigoSender.send(any())).willReturn(NotificationSender.SendResult.failure("vendor error"));
        given(notificationLogRepository.findById(1L)).willReturn(Optional.of(log));

        alimtalkWorker.processHigh(message);

        // SMS Fallback 큐로 재발행
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(jmsTemplate).convertAndSend(eq("noti.high.sms"), captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo("SMS");
        assertThat(captor.getValue().content()).isEqualTo("SMS 대체 내용");
        assertThat(captor.getValue().retryCount()).isEqualTo(0);

        verify(log).markFallback("SMS");
        verify(jmsTemplate, never()).convertAndSend(eq("noti.dlq.alimtalk"), any(Object.class));
    }

    @Test
    @DisplayName("알림톡 재시도 소진 + Fallback 없으면 DLQ 적재")
    void processHigh_no_fallback_to_dlq() {
        // HIGH maxRetry=3, retryCount=3 → 소진, fallback=null
        NotificationMessage message = new NotificationMessage(
                2L, "ALIMTALK", "HIGH", "01099999999",
                null, null, "내용", null, null, 3
        );
        NotificationLog log = mock(NotificationLog.class);

        given(nurigoSender.send(any())).willReturn(NotificationSender.SendResult.failure("vendor timeout"));
        given(notificationLogRepository.findById(2L)).willReturn(Optional.of(log));

        alimtalkWorker.processHigh(message);

        verify(jmsTemplate).convertAndSend(eq("noti.dlq.alimtalk"), any(NotificationMessage.class));
        verify(log).markFailed("vendor timeout");
        verify(notificationMetrics).recordDlq("ALIMTALK");
    }
}
