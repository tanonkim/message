package com.message.domain.sms.command;

public record SmsSendCommand(
        String recipient,
        String content
) {
}
