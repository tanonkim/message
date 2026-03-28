package com.message.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> sentCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> failedCounters = new ConcurrentHashMap<>();

    public void recordSent(String channel) {
        sentCounters.computeIfAbsent(channel, ch ->
                Counter.builder("notification.sent")
                        .tag("channel", ch.toLowerCase())
                        .description("발송 성공 건수")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordFailed(String channel) {
        failedCounters.computeIfAbsent(channel, ch ->
                Counter.builder("notification.failed")
                        .tag("channel", ch.toLowerCase())
                        .description("발송 실패 건수")
                        .register(meterRegistry)
        ).increment();
    }

    public void recordDlq(String channel) {
        Counter.builder("notification.dlq")
                .tag("channel", channel.toLowerCase())
                .description("DLQ 적재 건수")
                .register(meterRegistry)
                .increment();
    }

}
