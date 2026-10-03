package com.ureclive.urec_live_backend.dto;

/** Counts for the admin Equipment Issues stat cards and the sidebar badge. */
public class EquipmentIssueSummaryResponse {

    private long reported;
    private long acknowledged;
    private long inProgress;
    private long affectedMachines;

    public EquipmentIssueSummaryResponse(long reported, long acknowledged, long inProgress, long affectedMachines) {
        this.reported = reported;
        this.acknowledged = acknowledged;
        this.inProgress = inProgress;
        this.affectedMachines = affectedMachines;
    }

    public long getReported() { return reported; }
    public long getAcknowledged() { return acknowledged; }
    public long getInProgress() { return inProgress; }
    public long getAffectedMachines() { return affectedMachines; }
}
