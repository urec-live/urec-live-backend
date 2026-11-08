package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.Converter;

/**
 * An unknown channel reads as null rather than a guess, since acting on the wrong channel would
 * touch a different message with the same id.
 */
@Converter
public class ChatChannelConverter extends EnumStringConverter<ChatChannel> {
    public ChatChannelConverter() {
        super(ChatChannel.class, null);
    }
}
