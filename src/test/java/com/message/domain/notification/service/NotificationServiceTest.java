package com.message.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.blocklist.query.BlockCheckQuery;
import com.message.domain.blocklist.service.BlockService;
import com.message.domain.notification.controller.request.NotificationRequest;
import com.message.domain.notification.controller.response.NotificationResponse;
import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.enum_type.NotificationChannel;
import com.message.domain.notification.enum_type.NotificationStatus;
import com.message.domain.notification.repository.DetailNotificaionLogRepository;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.domain.notification.service.command.service.NotificationCommandService;
import com.message.domain.notification.service.command.usecase.NotificationCommandUseCase;
import com.message.global.exception.ApiException;
import com.message.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationCommandService notificationCommandUseCase;

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

    @Captor
    private ArgumentCaptor<BlockCheckQuery> blockCheckCaptor;

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
        NotificationResponse response = notificationCommandUseCase.request(request);

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
        NotificationResponse response = notificationCommandUseCase.request(request);

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
        assertThatThrownBy(() -> notificationCommandUseCase.request(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_REQUEST));

        verify(jmsTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("잘못된 채널 요청 시 400 반환")
    void request_invalidChannel() {
        NotificationRequest request = createRequest("KAKAO", "HIGH", "01012345678", null);

        assertThatThrownBy(() -> notificationCommandUseCase.request(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CHANNEL));
    }

    @Test
    @DisplayName("phone만 있는 요청 시 blockService에 phone이 담긴 BlockCheckQuery가 전달된다")
    void request_phoneOnly_passesCorrectBlockCheckQuery() {
        // given
        NotificationRequest request = new NotificationRequest(
                "order-service", "SMS", "NORMAL",
                new NotificationRequest.RecipientDto("01012345678", null),
                null, "테스트 메시지", null, null, null
        );
        NotificationLog savedLog = mock(NotificationLog.class);
        given(savedLog.getId()).willReturn(10L);
        given(detailNotificaionLogRepository.existsByIdempotencyKey(any())).willReturn(false);
        given(blockService.isBlocked(any())).willReturn(false);
        given(notificationLogRepository.save(any())).willReturn(savedLog);

        // when
        notificationCommandUseCase.request(request);

        // then
        verify(blockService).isBlocked(blockCheckCaptor.capture());
        BlockCheckQuery captured = blockCheckCaptor.getValue();
        assertThat(captured.phone()).isEqualTo("01012345678");
        assertThat(captured.email()).isNull();
    }

    @Test
    @DisplayName("email만 있는 요청 시 blockService에 email이 담긴 BlockCheckQuery가 전달된다")
    void request_emailOnly_passesCorrectBlockCheckQuery() {
        // given
        NotificationRequest request = new NotificationRequest(
                "order-service", "EMAIL", "NORMAL",
                new NotificationRequest.RecipientDto(null, "test@example.com"),
                null, "테스트 메시지", null, null, null
        );
        NotificationLog savedLog = mock(NotificationLog.class);
        given(savedLog.getId()).willReturn(11L);
        given(detailNotificaionLogRepository.existsByIdempotencyKey(any())).willReturn(false);
        given(blockService.isBlocked(any())).willReturn(false);
        given(notificationLogRepository.save(any())).willReturn(savedLog);

        // when
        notificationCommandUseCase.request(request);

        // then
        verify(blockService).isBlocked(blockCheckCaptor.capture());
        BlockCheckQuery captured = blockCheckCaptor.getValue();
        assertThat(captured.phone()).isNull();
        assertThat(captured.email()).isEqualTo("test@example.com");
    }

}
