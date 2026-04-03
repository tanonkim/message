package com.message.domain.notification.message;

import java.util.Map;

public record NotificationMessage(
        Long notificationLogId,
        String channel,
        String priority,
        String recipient,
        String templateCode,
        Map<String, String> variables,
        String content,
        String fallbackChannel,
        String fallbackContent,
        int retryCount
) {

    public String queueName() {
        return "noti." + priority.toLowerCase() + "." + channel.toLowerCase();
    }

    public NotificationMessage withIncrementedRetry() {
        return new NotificationMessage(
                notificationLogId, channel, priority, recipient,
                templateCode, variables, content,
                fallbackChannel, fallbackContent, retryCount + 1
        );
    }

    public String dlqName() {
        return "noti.dlq." + channel.toLowerCase();
    }
}
