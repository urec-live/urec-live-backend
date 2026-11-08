package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.Converter;

/** Column converters for {@link ChatMessageReport}'s enums, so new values can be added later. */
public final class ReportEnumConverters {

    private ReportEnumConverters() {}

    @Converter
    public static class ReasonConverter extends EnumStringConverter<ReportReason> {
        public ReasonConverter() {
            super(ReportReason.class, ReportReason.OTHER);
        }
    }

    @Converter
    public static class StatusConverter extends EnumStringConverter<ReportStatus> {
        public StatusConverter() {
            super(ReportStatus.class, ReportStatus.OPEN);
        }
    }
}
