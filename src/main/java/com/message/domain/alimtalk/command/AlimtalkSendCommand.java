package com.message.domain.alimtalk.command;

import java.util.Map;

public record AlimtalkSendCommand(
        String recipient,
        String templateCode,
        Map<String, String> variables
) {
}
