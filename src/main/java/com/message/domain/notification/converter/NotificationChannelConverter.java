package com.message.domain.notification.converter;

import com.message.domain.notification.enum_type.NotificationChannel;
import com.message.global.converter.EnumAttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NotificationChannelConverter extends EnumAttributeConverter<NotificationChannel> {

    public NotificationChannelConverter() {
        super(NotificationChannel.class);
    }
}
