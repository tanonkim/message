package com.message.domain.push.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender.SendResult;
import com.message.domain.push.command.PushSendCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class FingerpushSenderTest {

    @Mock private FingerpushProperties fingerpushProperties;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private FingerpushSender fingerpushSender;

    @BeforeEach
    void setUp() {
        fingerpushSender = new FingerpushSender(fingerpushProperties, new ObjectMapper(), restClient);
    }

    private void givenRestClientChainSetup() {
        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(anyString());
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
    }

    private void givenRestClientReturns(String responseBody) {
        givenRestClientChainSetup();
        given(responseSpec.body(String.class)).willReturn(responseBody);
    }

    private String 토큰_생성(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "token-" + i)
                .collect(Collectors.joining(","));
    }

    // ─────────────────────────────────────────────────────────────────
    // 변환 테스트 — NotificationMessage → PushSendCommand 변환 검증
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("send() 변환 검증")
    class ConversionTest {

        @Test
        @DisplayName("NotificationMessage의 recipient와 content가 PushSendCommand로 올바르게 변환된다")
        void send_올바른_PushSendCommand로_변환된다() {
            FingerpushSender spySender = spy(fingerpushSender);
            ArgumentCaptor<PushSendCommand> captor = ArgumentCaptor.forClass(PushSendCommand.class);
            doReturn(SendResult.success("mock-id", 0)).when(spySender).doSend(captor.capture());

            NotificationMessage message = new NotificationMessage(
                    1L, "PUSH", "NORMAL", "device-token-abc",
                    null, null, "테스트 푸시 메시지", null, null, 0
            );

            spySender.send(message);

            PushSendCommand captured = captor.getValue();
            assertThat(captured.recipient()).isEqualTo("device-token-abc");
            assertThat(captured.content()).isEqualTo("테스트 푸시 메시지");
        }

        @Test
        @DisplayName("content가 null인 NotificationMessage도 PushSendCommand로 변환된다")
        void send_content_null_변환된다() {
            FingerpushSender spySender = spy(fingerpushSender);
            ArgumentCaptor<PushSendCommand> captor = ArgumentCaptor.forClass(PushSendCommand.class);
            doReturn(SendResult.success("mock-id", 0)).when(spySender).doSend(captor.capture());

            NotificationMessage message = new NotificationMessage(
                    2L, "PUSH", "NORMAL", "device-token-xyz",
                    null, null, null, null, null, 0
            );

            spySender.send(message);

            assertThat(captor.getValue().content()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 발송 로직 테스트 — PushSendCommand 기반, notification 도메인 의존 없음
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("doSend() 발송 로직 검증")
    class SendLogicTest {

        @BeforeEach
        void setUp() {
            given(fingerpushProperties.appId()).willReturn("test-app-id");
            given(fingerpushProperties.apiKey()).willReturn("test-api-key");
        }

        @Test
        @DisplayName("단일 수신자 발송 성공")
        void 단일_수신자_발송_성공() {
            givenRestClientReturns("response-001");
            PushSendCommand command = new PushSendCommand("device-token-abc", "테스트 푸시 메시지");

            SendResult result = fingerpushSender.doSend(command);

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("single-device-token-abc");
        }

        @Test
        @DisplayName("수신자가 null이면 전체 발송")
        void 수신자_null_전체_발송_성공() {
            givenRestClientReturns("entire-msg-001");
            PushSendCommand command = new PushSendCommand(null, "전체 공지 메시지");

            SendResult result = fingerpushSender.doSend(command);

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("entire-entire-msg-001");
        }

        @Test
        @DisplayName("수신자가 공백이면 전체 발송")
        void 수신자_공백_전체_발송_성공() {
            givenRestClientReturns("entire-msg-002");
            PushSendCommand command = new PushSendCommand("   ", "전체 공지 메시지");

            SendResult result = fingerpushSender.doSend(command);

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("entire-entire-msg-002");
        }

        @Test
        @DisplayName("다중 수신자 - 토큰 앞뒤 공백 trim 및 빈 값 필터링")
        void 다중_수신자_공백_토큰_trim_필터링_후_발송() {
            givenRestClientReturns("batch-id-trim");
            PushSendCommand command = new PushSendCommand(" token-a , token-b,,  , token-c ", "푸시 메시지");

            SendResult result = fingerpushSender.doSend(command);

            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("다중 수신자 단일 배치 전체 성공")
        void 다중_수신자_단일_배치_성공() {
            givenRestClientReturns("batch-id-001");
            PushSendCommand command = new PushSendCommand("token-1,token-2,token-3", "배치 발송 테스트");

            SendResult result = fingerpushSender.doSend(command);

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("target-batch-id-001");
        }

        @Test
        @DisplayName("다중 수신자 배치 부분 실패 시 성공 반환 (501개 → 2배치, 2번째 실패)")
        void 다중_수신자_배치_부분_실패_성공_반환() {
            givenRestClientChainSetup();
            given(responseSpec.body(String.class))
                    .willReturn("batch-1-id")
                    .willThrow(new RuntimeException("2번째 배치 네트워크 오류"));

            PushSendCommand command = new PushSendCommand(토큰_생성(501), "대량 발송");

            SendResult result = fingerpushSender.doSend(command);

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("target-batch-1-id");
        }

        @Test
        @DisplayName("다중 수신자 전체 배치 실패 시 RuntimeException 발생 (Circuit Breaker가 실패로 집계)")
        void 다중_수신자_전체_배치_실패_RuntimeException_발생() {
            givenRestClientChainSetup();
            given(responseSpec.body(String.class))
                    .willThrow(new RuntimeException("API 연결 실패"));

            PushSendCommand command = new PushSendCommand("token-1,token-2", "테스트 메시지");

            assertThatThrownBy(() -> fingerpushSender.doSend(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("전체 배치 발송 실패");
        }
    }
}