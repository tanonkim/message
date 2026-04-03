package com.message.domain.push.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.fingerpush")
public record FingerpushProperties(String apiKey, String appId) {
}
