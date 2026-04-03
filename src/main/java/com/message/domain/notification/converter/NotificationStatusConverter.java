package com.message.domain.notification.converter;

import com.message.domain.notification.enum_type.NotificationStatus;
import com.message.global.converter.EnumAttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NotificationStatusConverter extends EnumAttributeConverter<NotificationStatus> {

    public NotificationStatusConverter() {
        super(NotificationStatus.class);
    }
}
