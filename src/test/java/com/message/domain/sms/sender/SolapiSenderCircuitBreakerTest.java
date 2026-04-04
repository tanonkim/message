package com.message.domain.sms.sender;

import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.NotificationSender.SendResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SolapiSenderCircuitBreakerTest {

    @Mock private SolapiProperties solapiProperties;

    private SolapiSender solapiSender;

    private static final NotificationMessage 테스트_메시지 = new NotificationMessage(
            1L, "SMS", "CRITICAL", "01012345678",
            null, null, "테스트 메시지", null, null, 0
    );

    @BeforeEach
    void setUp() {
        solapiSender = new SolapiSender(solapiProperties, new com.fasterxml.jackson.databind.ObjectMapper(), null);
    }

    @Test
    @DisplayName("서킷 OPEN 시 fallback이 호출되어 failure 반환")
    void 서킷_오픈_시_fallback_failure_반환() {
        CallNotPermittedException 서킷오픈_예외 = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.of("solapi", CircuitBreakerConfig.ofDefaults())
        );

        SendResult result = solapiSender.fallback(테스트_메시지, 서킷오픈_예외);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Circuit Breaker OPEN: SMS 서비스 일시 중단");
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("실패율 100% 초과 시 서킷이 OPEN 상태로 전환")
    void 실패_임계값_초과_시_서킷_OPEN_전환() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(100)
                .minimumNumberOfCalls(2)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("solapi-test", config);

        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("Solapi 연결 실패"));
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("Solapi 연결 실패"));

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("서킷 OPEN 상태에서 호출 시 CallNotPermittedException 발생")
    void 서킷_OPEN_상태에서_호출_시_CallNotPermittedException_발생() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(100)
                .minimumNumberOfCalls(2)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("solapi-test", config);

        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("실패1"));
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("실패2"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                circuitBreaker.executeSupplier(() -> "API 호출")
        ).isInstanceOf(CallNotPermittedException.class);
    }
}
