package com.message.domain.push.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;

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
        given(fingerpushProperties.appId()).willReturn("test-app-id");
        given(fingerpushProperties.apiKey()).willReturn("test-api-key");
    }

    // RequestBodySpec은 RequestHeadersSpec을 상속하므로 body() 이후에도 재사용
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

    // 테스트용 토큰 n개를 콤마 구분 문자열로 생성
    private String 토큰_생성(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "token-" + i)
                .collect(Collectors.joining(","));
    }

    @Test
    @DisplayName("단일 수신자 발송 성공")
    void 단일_수신자_발송_성공() {
        givenRestClientReturns("response-001");
        NotificationMessage message = new NotificationMessage(
                1L, "PUSH", "NORMAL", "device-token-abc",
                null, null, "테스트 푸시 메시지", null, null, 0
        );

        SendResult result = fingerpushSender.send(message);

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("single-device-token-abc");
    }

    @Test
    @DisplayName("수신자가 null이면 전체 발송")
    void 수신자_null_전체_발송_성공() {
        givenRestClientReturns("entire-msg-001");
        NotificationMessage message = new NotificationMessage(
                2L, "PUSH", "NORMAL", null,
                null, null, "전체 공지 메시지", null, null, 0
        );

        SendResult result = fingerpushSender.send(message);
        System.out.println(result);

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("entire-entire-msg-001");
    }

    @Test
    @DisplayName("수신자가 공백이면 전체 발송")
    void 수신자_공백_전체_발송_성공() {
        givenRestClientReturns("entire-msg-002");
        NotificationMessage message = new NotificationMessage(
                3L, "PUSH", "NORMAL", "   ",
                null, null, "전체 공지 메시지", null, null, 0
        );

        SendResult result = fingerpushSender.send(message);

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("entire-entire-msg-002");
    }

    @Test
    @DisplayName("다중 수신자 - 토큰 앞뒤 공백 trim 및 빈 값 필터링")
    void 다중_수신자_공백_토큰_trim_필터링_후_발송() {
        givenRestClientReturns("batch-id-trim");
        // 공백 포함 토큰, 연속 콤마(빈 값) 포함
        NotificationMessage message = new NotificationMessage(
                4L, "PUSH", "NORMAL", " token-a , token-b,,  , token-c ",
                null, null, "푸시 메시지", null, null, 0
        );

        SendResult result = fingerpushSender.send(message);

        // 공백/빈 값 제거 후 3개 토큰으로 정상 발송
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("다중 수신자 단일 배치 전체 성공")
    void 다중_수신자_단일_배치_성공() {
        givenRestClientReturns("batch-id-001");
        NotificationMessage message = new NotificationMessage(
                5L, "PUSH", "HIGH", "token-1,token-2,token-3",
                null, null, "배치 발송 테스트", null, null, 0
        );

        SendResult result = fingerpushSender.send(message);

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("target-batch-id-001");
    }

    @Test
    @DisplayName("다중 수신자 배치 부분 실패 시 성공 반환 (501개 → 2배치, 2번째 실패)")
    void 다중_수신자_배치_부분_실패_성공_반환() {
        // 501개 토큰 → 배치 2개 (500 + 1)
        // 첫 번째 배치 성공, 두 번째 배치 실패
        givenRestClientChainSetup();
        given(responseSpec.body(String.class))
                .willReturn("batch-1-id")
                .willThrow(new RuntimeException("2번째 배치 네트워크 오류"));

        NotificationMessage message = new NotificationMessage(
                6L, "PUSH", "NORMAL", 토큰_생성(501),
                null, null, "대량 발송", null, null, 0
        );

        SendResult result = fingerpushSender.send(message);

        // 일부 성공이므로 success 반환
        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("target-batch-1-id");
    }

    @Test
    @DisplayName("다중 수신자 전체 배치 실패 시 RuntimeException 발생 (Circuit Breaker가 실패로 집계)")
    void 다중_수신자_전체_배치_실패_RuntimeException_발생() {
        givenRestClientChainSetup();
        given(responseSpec.body(String.class))
                .willThrow(new RuntimeException("API 연결 실패"));

        NotificationMessage message = new NotificationMessage(
                7L, "PUSH", "NORMAL", "token-1,token-2",
                null, null, "테스트 메시지", null, null, 0
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fingerpushSender.send(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("전체 배치 발송 실패");
    }
}
