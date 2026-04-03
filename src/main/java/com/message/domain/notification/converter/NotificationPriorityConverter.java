package com.message.domain.notification.converter;

import com.message.domain.notification.enums.NotificationPriority;
import com.message.global.converter.EnumAttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NotificationPriorityConverter extends EnumAttributeConverter<NotificationPriority> {

    public NotificationPriorityConverter() {
        super(NotificationPriority.class);
    }
}