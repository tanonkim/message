package com.message.domain.push.worker;

import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.domain.notification.sender.AbstractWorker;
import com.message.domain.push.sender.FingerpushSender;
import com.message.global.metrics.NotificationMetrics;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class PushWorker extends AbstractWorker {

    private final FingerpushSender fingerpushSender;

    public PushWorker(JmsTemplate jmsTemplate, NotificationLogRepository notificationLogRepository,
                      NotificationMetrics notificationMetrics, FingerpushSender fingerpushSender) {
        super(jmsTemplate, notificationLogRepository, notificationMetrics);
        this.fingerpushSender = fingerpushSender;
    }

    @JmsListener(destination = "noti.critical.push", containerFactory = "jmsListenerContainerFactory")
    public void processCritical(NotificationMessage message) {
        process(message, fingerpushSender);
    }

    @JmsListener(destination = "noti.high.push", containerFactory = "jmsListenerContainerFactory")
    public void processHigh(NotificationMessage message) {
        process(message, fingerpushSender);
    }

    @JmsListener(destination = "noti.normal.push", containerFactory = "jmsListenerContainerFactory")
    public void processNormal(NotificationMessage message) {
        process(message, fingerpushSender);
    }

    @JmsListener(destination = "noti.low.push", containerFactory = "jmsListenerContainerFactory")
    public void processLow(NotificationMessage message) {
        process(message, fingerpushSender);
    }


}
