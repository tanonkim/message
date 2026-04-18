package com.message.domain.email.worker;

import com.message.domain.email.sender.SesV2Sender;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.AbstractWorker;
import com.message.domain.notification.service.NotificationLogCommandService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmailWorker extends AbstractWorker {

    private final SesV2Sender sesV2Sender;

    public EmailWorker(JmsTemplate jmsTemplate,
                       NotificationLogCommandService notificationLogCommandService,
                       SesV2Sender sesV2Sender) {
        super(jmsTemplate, notificationLogCommandService);
        this.sesV2Sender = sesV2Sender;
    }

    @JmsListener(destination = "noti.critical.email", containerFactory = "jmsListenerContainerFactory")
    public void processCritical(NotificationMessage message) {
        process(message, sesV2Sender);
    }

    @JmsListener(destination = "noti.high.email", containerFactory = "jmsListenerContainerFactory")
    public void processHigh(NotificationMessage message) {
        process(message, sesV2Sender);
    }

    @JmsListener(destination = "noti.normal.email", containerFactory = "jmsListenerContainerFactory")
    public void processNormal(NotificationMessage message) {
        process(message, sesV2Sender);
    }

    @JmsListener(destination = "noti.low.email", containerFactory = "jmsListenerContainerFactory")
    public void processLow(NotificationMessage message) {
        process(message, sesV2Sender);
    }
}
