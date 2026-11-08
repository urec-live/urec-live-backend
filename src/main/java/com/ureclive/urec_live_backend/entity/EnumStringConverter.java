package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.AttributeConverter;

/**
 * Stores an enum as its name in a plain varchar. Unlike {@code @Enumerated(STRING)}, Hibernate
 * generates no check constraint for a converted column, so adding an enum value later doesn't break
 * inserts on databases kept up to date with ddl-auto=update (which never widens those constraints).
 * A name this build doesn't know (a value added by a newer deploy, read mid-rollout or after a
 * rollback) reads like null, so one such row can't fail a whole query.
 */
abstract class EnumStringConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;
    private final E whenNull;

    protected EnumStringConverter(Class<E> type, E whenNull) {
        this.type = type;
        this.whenNull = whenNull;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public E convertToEntityAttribute(String value) {
        if (value == null) return whenNull;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return whenNull;
        }
    }
}
