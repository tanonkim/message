package com.message.domain.sms.worker;

import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.AbstractWorker;
import com.message.domain.notification.service.NotificationLogCommandService;
import com.message.domain.sms.sender.SolapiSender;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class SmsWorker extends AbstractWorker {

    private final SolapiSender solapiSender;

    public SmsWorker(JmsTemplate jmsTemplate,
                     NotificationLogCommandService notificationLogCommandService,
                     SolapiSender solapiSender) {
        super(jmsTemplate, notificationLogCommandService);
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
