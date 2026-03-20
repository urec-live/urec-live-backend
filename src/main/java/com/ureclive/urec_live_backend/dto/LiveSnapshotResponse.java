package com.ureclive.urec_live_backend.dto;

public class LiveSnapshotResponse {
    private long totalMachines;
    private long occupiedMachines;
    private long availableMachines;
    private long reservedMachines;
    private long activeUsers;

    public LiveSnapshotResponse(long totalMachines, long occupiedMachines,
                                long availableMachines, long reservedMachines) {
        this.totalMachines = totalMachines;
        this.occupiedMachines = occupiedMachines;
        this.availableMachines = availableMachines;
        this.reservedMachines = reservedMachines;
        this.activeUsers = occupiedMachines; // 1 user per occupied machine
    }

    public long getTotalMachines() { return totalMachines; }
    public long getOccupiedMachines() { return occupiedMachines; }
    public long getAvailableMachines() { return availableMachines; }
    public long getReservedMachines() { return reservedMachines; }
    public long getActiveUsers() { return activeUsers; }
}
