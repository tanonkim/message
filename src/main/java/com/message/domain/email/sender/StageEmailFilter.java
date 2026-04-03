package com.message.domain.email.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "notification.stage")
public record StageEmailFilter(List<String> allowedDomains) {

    public boolean isAllowed(String email) {
        if (allowedDomains == null || allowedDomains.isEmpty()) return true;
        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
        return allowedDomains.contains(domain);
    }
}
