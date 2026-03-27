package com.message.global.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "notification")
public record ApiKeyProperties(Map<String, String> apiKeys) {

    public boolean isValid(String apiKey) {
        if (apiKey == null || apiKeys == null) return false;
        return apiKeys.containsKey(apiKey);
    }

}
