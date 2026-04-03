package com.message.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.blocklist.service.BlockService;
import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.enum_type.NotificationChannel;
import com.message.domain.notification.enum_type.NotificationStatus;
import com.message.domain.notification.repository.DetailNotificaionLogRepository;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.global.exception.ApiException;
import com.message.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private SaveNotificationService notificationService;

    @Mock
    private BlockService blockService;

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
        given(savedLog.getId()).willReturn(1L);
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

    @Test
    @DisplayName("차단된 수신자 요청 시 발송 없이 PENDING 반환")
    void request_blockedRecipient() {
        // given
        NotificationRequest request = createRequest("SMS", "NORMAL", "01099999999", null);
        NotificationLog blockedLog = mock(NotificationLog.class);
        given(blockedLog.getId()).willReturn(2L);
        given(blockedLog.getChannel()).willReturn(NotificationChannel.SMS);
        given(blockedLog.getStatus()).willReturn(NotificationStatus.FAILED);
        given(detailNotificaionLogRepository.existsByIdempotencyKey(any())).willReturn(false);
        given(blockService.isBlocked(any())).willReturn(true);
        given(notificationLogRepository.save(any())).willReturn(blockedLog);

        // when
        NotificationResponse response = notificationService.request(request);

        // then
        assertThat(response.notificationLogId()).isEqualTo(2L);
        verify(jmsTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("중복 idempotencyKey 요청 시 409 반환")
    void request_duplicate() {
        // given
        NotificationRequest request = createRequest("SMS", "HIGH", "01012345678", "dup-key");
        given(detailNotificaionLogRepository.existsByIdempotencyKey("dup-key")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> notificationService.request(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_REQUEST));

        verify(jmsTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("잘못된 채널 요청 시 400 반환")
    void request_invalidChannel() {
        NotificationRequest request = createRequest("KAKAO", "HIGH", "01012345678", null);

        assertThatThrownBy(() -> notificationService.request(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CHANNEL));
    }


}
