package com.message.global.shutdown;


import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveMqShutdownManager {

    private final JmsListenerEndpointRegistry jmsListenerEndpointRegistry;

    @PreDestroy
    public void gracefulShutdown() {
        log.info("Initiating graceful ActiveMQ shutdown...");

        try {
            CountDownLatch latch = new CountDownLatch(1);
            jmsListenerEndpointRegistry.stop(latch::countDown); // 처리 중인 메시지 완료 시 콜백 호출
            boolean completed = latch.await(30, TimeUnit.SECONDS);  // 최대 30초까지 실제 완료 대기
            if (completed) {
                log.info("ActiveMQ graceful shutdown complete");
            } else {
                log.warn("ActiveMQ shutdown timed out after 30 seconds");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Shutdown interrupted");
        }
        catch (Exception e) {
            log.error("Error during ActiveMQ shutdown", e);
        }
    }
}
