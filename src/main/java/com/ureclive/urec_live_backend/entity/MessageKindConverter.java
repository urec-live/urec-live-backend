package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.Converter;

/** Rows written before message kinds existed read as TEXT. */
@Converter
public class MessageKindConverter extends EnumStringConverter<MessageKind> {
    public MessageKindConverter() {
        super(MessageKind.class, MessageKind.TEXT);
    }
}
