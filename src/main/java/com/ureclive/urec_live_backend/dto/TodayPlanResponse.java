package com.ureclive.urec_live_backend.dto;

import java.util.List;

public class TodayPlanResponse {

    private Integer dayOfWeek;
    private String label;
    private String planName;
    private boolean planActive;
    private List<TodayGoalItem> items;

    public TodayPlanResponse() {}

    public static class TodayGoalItem {
        private String muscleGroup;
        private Integer targetCount;
        private Integer completedCount;
        private boolean completed;

        public TodayGoalItem() {}

        public TodayGoalItem(String muscleGroup, Integer targetCount, Integer completedCount) {
            this.muscleGroup = muscleGroup;
            this.targetCount = targetCount;
            this.completedCount = completedCount;
            this.completed = completedCount >= targetCount;
        }

        public String getMuscleGroup() { return muscleGroup; }
        public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

        public Integer getTargetCount() { return targetCount; }
        public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }

        public Integer getCompletedCount() { return completedCount; }
        public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }

        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
    }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public boolean isPlanActive() { return planActive; }
    public void setPlanActive(boolean planActive) { this.planActive = planActive; }

    public List<TodayGoalItem> getItems() { return items; }
    public void setItems(List<TodayGoalItem> items) { this.items = items; }
}
