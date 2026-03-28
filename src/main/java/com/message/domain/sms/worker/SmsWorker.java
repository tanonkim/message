package com.message.domain.sms.worker;

import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.repository.NotificationLogRepository;
import com.message.domain.notification.sender.AbstractWorker;
import com.message.domain.sms.sender.SolapiSender;
import com.message.global.metrics.NotificationMetrics;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class SmsWorker extends AbstractWorker {

    private final SolapiSender solapiSender;

    public SmsWorker(JmsTemplate jmsTemplate, NotificationLogRepository notificationLogRepository,
                     NotificationMetrics notificationMetrics, SolapiSender solapiSender) {
        super(jmsTemplate, notificationLogRepository, notificationMetrics);
        this.solapiSender = solapiSender;
    }

    @JmsListener(destination = "noti.critical.sms", containerFactory = "jmsListenerContainerFactory")
    public void processCritical(NotificationMessage message) {
        process(message, solapiSender);
    }

    @JmsListener(destination = "noti.high.sms", containerFactory = "jmsListenerContainerFactory")
    public void processHigh(NotificationMessage message) {
        process(message, solapiSender);
    }

    @JmsListener(destination = "noti.normal.sms", containerFactory = "jmsListenerContainerFactory")
    public void processNormal(NotificationMessage message) {
        process(message, solapiSender);
    }

    @JmsListener(destination = "noti.low.sms", containerFactory = "jmsListenerContainerFactory")
    public void processLow(NotificationMessage message) {
        process(message, solapiSender);
    }

}
