package com.message.global.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.crypto")
public record CryptoProperties(String key, String iv) {
}

