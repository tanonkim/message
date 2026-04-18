package com.message.domain.alimtalk.worker;

import com.message.domain.alimtalk.sender.NurigoSender;
import com.message.domain.notification.message.NotificationMessage;
import com.message.domain.notification.sender.AbstractWorker;
import com.message.domain.notification.service.NotificationLogCommandService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlimtalkWorker extends AbstractWorker {

    private final NurigoSender nurigoSender;

    public AlimtalkWorker(JmsTemplate jmsTemplate,
                          NotificationLogCommandService notificationLogCommandService,
                          NurigoSender nurigoSender) {
        super(jmsTemplate, notificationLogCommandService);
        this.nurigoSender = nurigoSender;
    }

    @JmsListener(destination = "noti.critical.alimtalk", containerFactory = "jmsListenerContainerFactory")
    public void processCritical(NotificationMessage message) {
        process(message, nurigoSender);
    }

    @JmsListener(destination = "noti.high.alimtalk", containerFactory = "jmsListenerContainerFactory")
    public void processHigh(NotificationMessage message) {
        process(message, nurigoSender);
    }

    @JmsListener(destination = "noti.normal.alimtalk", containerFactory = "jmsListenerContainerFactory")
    public void processNormal(NotificationMessage message) {
        process(message, nurigoSender);
    }

    @JmsListener(destination = "noti.low.alimtalk", containerFactory = "jmsListenerContainerFactory")
    public void processLow(NotificationMessage message) {
        process(message, nurigoSender);
    }


}
