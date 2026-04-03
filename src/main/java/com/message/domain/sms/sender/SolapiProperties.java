package com.message.domain.sms.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.solapi")
public record SolapiProperties(String apiKey, String apiSecret, String fromNumber) {
}
