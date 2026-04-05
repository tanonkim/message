package com.message.domain.push.command;

public record PushSendCommand(
        String recipient,
        String content
) {
}
