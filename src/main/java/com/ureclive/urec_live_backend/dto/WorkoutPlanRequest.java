package com.ureclive.urec_live_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class WorkoutPlanRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Valid
    private List<DayPlanDto> days;

    public WorkoutPlanRequest() {}

    public static class DayPlanDto {
        @NotNull
        private Integer dayOfWeek; // 1=Monday, 7=Sunday

        @Size(max = 100)
        private String label;

        @Valid
        private List<DayPlanItemDto> items;

        public DayPlanDto() {}

        public Integer getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public List<DayPlanItemDto> getItems() { return items; }
        public void setItems(List<DayPlanItemDto> items) { this.items = items; }
    }

    public static class DayPlanItemDto {
        @NotBlank
        private String muscleGroup;

        @NotNull
        private Integer targetCount;

        private Integer sortOrder;

        public DayPlanItemDto() {}

        public String getMuscleGroup() { return muscleGroup; }
        public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

        public Integer getTargetCount() { return targetCount; }
        public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<DayPlanDto> getDays() { return days; }
    public void setDays(List<DayPlanDto> days) { this.days = days; }
}
