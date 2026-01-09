package com.ureclive.urec_live_backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class WeeklySplitResponse {
    private String mode;
    private Map<String, List<String>> manualSplit;
    private Instant updatedAt;

    public WeeklySplitResponse(String mode, Map<String, List<String>> manualSplit, Instant updatedAt) {
        this.mode = mode;
        this.manualSplit = manualSplit;
        this.updatedAt = updatedAt;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Map<String, List<String>> getManualSplit() {
        return manualSplit;
    }

    public void setManualSplit(Map<String, List<String>> manualSplit) {
        this.manualSplit = manualSplit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
