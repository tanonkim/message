package com.message.global.converter;

import jakarta.persistence.AttributeConverter;

public abstract class EnumAttributeConverter<T extends Enum<T>> implements AttributeConverter<T, String> {

    private final Class<T> enumClass;

    protected EnumAttributeConverter(Class<T> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumClass, dbData);
    }
}