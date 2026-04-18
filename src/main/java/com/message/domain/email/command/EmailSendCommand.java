package com.message.domain.email.command;

public record EmailSendCommand(
        String recipient,
        String content,
        String templateCode
) {
}
