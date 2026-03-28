package com.message.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.repository.DetailNotificaionLogRepository;
import com.message.domain.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private SaveNotificationService notificationService;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private DetailNotificaionLogRepository detailNotificaionLogRepository;

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("정상 발송 요청 시 202 Accepted 반환")
    void request_success() {
        // given
        NotificationRequest request = createRequest("SMS", "CRITICAL", "01012345678", null);
        NotificationLog savedLog = mock(NotificationLog.class);
        given(savedLog.getNotificationLogId()).willReturn(1L);
        given(detailNotificaionLogRepository.existsByIdempotencyKey(any())).willReturn(false);
        given(notificationLogRepository.save(any())).willReturn(savedLog);

        // when
        NotificationResponse response = notificationService.request(request);

        // then
        assertThat(response.notificationLogId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(jmsTemplate).convertAndSend(anyString(), any(Object.class));
    }

    private NotificationRequest createRequest(String channel, String priority, String phone, String idempotencyKey) {
        return new NotificationRequest(
                "test-service", channel, priority,
                new NotificationRequest.RecipientDto(phone, null),
                null, "테스트 내용", null, idempotencyKey, null
        );
    }


}
