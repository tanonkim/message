package com.message.domain.alimtalk.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.nurigo")
public record NurigoProperties(String apiKey, String apiSecret, String fromNumber) {
}
