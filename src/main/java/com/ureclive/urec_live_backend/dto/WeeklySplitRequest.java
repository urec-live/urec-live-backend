package com.ureclive.urec_live_backend.dto;

import java.util.List;
import java.util.Map;

public class WeeklySplitRequest {
    private String mode;
    private Map<String, List<String>> manualSplit;

    public WeeklySplitRequest() {}

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
}
