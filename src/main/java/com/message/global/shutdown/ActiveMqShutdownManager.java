package com.message.global.shutdown;


import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveMqShutdownManager {

    private final JmsListenerEndpointRegistry jmsListenerEndpointRegistry;

    @PreDestroy
    public void gracefulShutdown() {
        log.info("Initiating graceful ActiveMQ shutdown...");

        try {
            jmsListenerEndpointRegistry.stop();
            Thread.sleep(3000); // 처리 중인 메시지 완료 대기
            log.info("ActiveMQ graceful shutdown complete");
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
