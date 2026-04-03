package com.message.domain.email.sender;

import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.ses")
public record SesProperties(String region, String fromEmail) {
}
