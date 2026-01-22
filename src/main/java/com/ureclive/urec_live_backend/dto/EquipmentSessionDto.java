package com.ureclive.urec_live_backend.dto;

import java.time.Instant;

public class EquipmentSessionDto {
    private Long id;
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    private String endReason;
    private EquipmentSummary equipment;

    public EquipmentSessionDto() {}

    public EquipmentSessionDto(
            Long id,
            String status,
            Instant startedAt,
            Instant endedAt,
            String endReason,
            EquipmentSummary equipment
    ) {
        this.id = id;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endReason = endReason;
        this.equipment = equipment;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getEndReason() {
        return endReason;
    }

    public void setEndReason(String endReason) {
        this.endReason = endReason;
    }

    public EquipmentSummary getEquipment() {
        return equipment;
    }

    public void setEquipment(EquipmentSummary equipment) {
        this.equipment = equipment;
    }

    public static class EquipmentSummary {
        private Long id;
        private String code;
        private String name;

        public EquipmentSummary() {}

        public EquipmentSummary(Long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
