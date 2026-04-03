package com.message.global.messaging;


import com.message.domain.notification.message.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqHandler {

    private final SlackNotifier slackNotifier;

    @JmsListener(destination = "noti.dlq.sms", containerFactory = "jmsListenerContainerFactory")
    public void handleSmsDlq(NotificationMessage message) {
        handleDlq("noti.dlq.sms", message);
    }

    @JmsListener(destination = "noti.dlq.alimtalk", containerFactory = "jmsListenerContainerFactory")
    public void handleAlimtalkDlq(NotificationMessage message) {
        handleDlq("noti.dlq.alimtalk", message);
    }

    @JmsListener(destination = "noti.dlq.email", containerFactory = "jmsListenerContainerFactory")
    public void handleEmailDlq(NotificationMessage message) {
        handleDlq("noti.dlq.email", message);
    }

    @JmsListener(destination = "noti.dlq.push", containerFactory = "jmsListenerContainerFactory")
    public void handlePushDlq(NotificationMessage message) {
        handleDlq("noti.dlq.push", message);
    }

    private void handleDlq(String queueName, NotificationMessage message) {
        log.error("DLQ received: queue={}, logId={}, channel={}, retryCount={}",
                queueName, message.notificationLogId(), message.channel(), message.retryCount());

        slackNotifier.sendDlqAlert(
                queueName,
                message.notificationLogId(),
                String.format("채널=%s, 재시도=%d회 소진", message.channel(), message.retryCount())
        );

    }
}
