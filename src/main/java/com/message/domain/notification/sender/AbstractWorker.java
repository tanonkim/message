package com.message.domain.notification.sender;

import com.message.domain.notification.entity.NotificationLog;
import com.message.domain.notification.enums.NotificationPriority;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.global.metrics.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ScheduledMessage;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Slf4j
@RequiredArgsConstructor
public abstract class AbstractWorker {

    protected final JmsTemplate jmsTemplate;
    protected final NotificationLogRepository notificationLogRepository;
    protected final NotificationMetrics notificationMetrics;

    @Transactional
    protected void process(NotificationMessage message, NotificationSender sender) {

        NotificationSender.SendResult result = sender.send(message);

        NotificationLog notificationLog = notificationLogRepository.findById(message.notificationLogId())
                .orElseThrow(() -> new IllegalStateException("NotificationLog not found: " + message.notificationLogId()));

        if (result.success()) {
            notificationLog.markSent(result.providerMessageId(), BigDecimal.valueOf(result.cost()));
            notificationMetrics.recordSent(message.channel());
        } else {
            handleFailure(message, notificationLog, result.errorMessage());
        }
    }

    private void handleFailure(NotificationMessage message, NotificationLog notificationLog, String errorMessage) {
        NotificationPriority priority = NotificationPriority.valueOf(message.priority());
        int maxRetry = priority.getMaxRetry();

        if (message.retryCount() < maxRetry) {
            NotificationMessage retryMessage = message.withIncrementedRetry();
            long delayMs = priority.getRetryDelayMs();

            if (delayMs > 0) {
                // todo check : AMQ_SCHEDULED_DELAY -> ActiveMQ broker scheduler plugin 필요 (activemq.xml에 <broker schedulerSupport="true">)
                jmsTemplate.convertAndSend(message.queueName(), retryMessage, m -> {
                    m.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delayMs);
                    return m;
                });
                log.warn("Send failed, retrying [{}/{}] with {}ms delay: logId={}, error={}",
                        message.retryCount() + 1, maxRetry, delayMs, message.notificationLogId(), errorMessage);
            } else {
                jmsTemplate.convertAndSend(message.queueName(), retryMessage);
                log.warn("Send failed, retrying [{}/{}] immediately: logId={}, error={}",
                        message.retryCount() + 1, maxRetry, message.notificationLogId(), errorMessage);
            }
        } else {
            onMaxRetryExceeded(message, notificationLog, errorMessage);
        }
    }

    private void onMaxRetryExceeded(NotificationMessage message, NotificationLog notificationLog, String errorMessage) {
        if (message.fallbackChannel() != null) {
            NotificationMessage fallbackMessage = new NotificationMessage(
                    message.notificationLogId(), message.fallbackChannel(), message.priority(),
                    message.recipient(), null, null, message.fallbackContent(), null, null, 0
            );
            jmsTemplate.convertAndSend(fallbackMessage.queueName(), fallbackMessage);
            notificationLog.markFallback(message.fallbackChannel());
            log.warn("Falling back to {}: logId={}", message.fallbackChannel(), message.notificationLogId());
        } else {
            jmsTemplate.convertAndSend(message.dlqName(), message);
            notificationLog.markFailed(errorMessage);
            notificationMetrics.recordFailed(message.channel());
            notificationMetrics.recordDlq(message.channel());
            log.error("Max retry exceeded, moved to DLQ: logId={}, dlq={}", message.notificationLogId(), message.dlqName());
        }
    }
}
