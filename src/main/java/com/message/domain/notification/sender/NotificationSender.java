package com.message.domain.notification.sender;

import com.message.domain.notification.message.NotificationMessage;

public interface NotificationSender {

    SendResult send(NotificationMessage message);

    record SendResult(boolean success, String providerMessageId, String errorMessage, double cost) {

        public static SendResult success(String providerMessageId, double cost) {
            return new SendResult(true, providerMessageId, null, cost);
        }

        public static SendResult failure(String errorMessage) {
            return new SendResult(false, null, errorMessage, 0);
        }
    }
}
