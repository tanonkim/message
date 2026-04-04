package com.message.domain.email.sender;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SesV2SenderCircuitBreakerTest {

    @Test
    @DisplayName("실패율 임계값 초과 시 서킷이 OPEN 상태로 전환")
    void 실패_임계값_초과_시_서킷_OPEN_전환() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .failureRateThreshold(100)
                .minimumNumberOfCalls(2)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("ses-test", config);

        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("AWS SES 응답 없음"));
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("AWS SES 응답 없음"));

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
        CircuitBreaker circuitBreaker = CircuitBreaker.of("ses-test", config);

        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("실패1"));
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS, new RuntimeException("실패2"));

        assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> "API 호출"))
                .isInstanceOf(CallNotPermittedException.class);
    }
}
