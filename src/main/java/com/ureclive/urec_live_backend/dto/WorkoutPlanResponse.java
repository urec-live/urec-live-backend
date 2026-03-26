package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.DayPlan;
import com.ureclive.urec_live_backend.entity.DayPlanItem;
import com.ureclive.urec_live_backend.entity.WorkoutPlan;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class WorkoutPlanResponse {

    private Long id;
    private String name;
    private Boolean active;
    private List<DayPlanDto> days;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkoutPlanResponse() {}

    public static class DayPlanDto {
        private Long id;
        private Integer dayOfWeek;
        private String label;
        private List<DayPlanItemDto> items;

        public DayPlanDto() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Integer getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public List<DayPlanItemDto> getItems() { return items; }
        public void setItems(List<DayPlanItemDto> items) { this.items = items; }
    }

    public static class DayPlanItemDto {
        private Long id;
        private String muscleGroup;
        private Integer targetCount;
        private Integer sortOrder;

        public DayPlanItemDto() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getMuscleGroup() { return muscleGroup; }
        public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

        public Integer getTargetCount() { return targetCount; }
        public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static WorkoutPlanResponse from(WorkoutPlan plan) {
        WorkoutPlanResponse dto = new WorkoutPlanResponse();
        dto.id = plan.getId();
        dto.name = plan.getName();
        dto.active = plan.getActive();
        dto.createdAt = plan.getCreatedAt();
        dto.updatedAt = plan.getUpdatedAt();

        if (plan.getDayPlans() != null) {
            dto.days = plan.getDayPlans().stream().map(dp -> {
                DayPlanDto dayDto = new DayPlanDto();
                dayDto.id = dp.getId();
                dayDto.dayOfWeek = dp.getDayOfWeek();
                dayDto.label = dp.getLabel();

                if (dp.getItems() != null) {
                    dayDto.items = dp.getItems().stream().map(item -> {
                        DayPlanItemDto itemDto = new DayPlanItemDto();
                        itemDto.id = item.getId();
                        itemDto.muscleGroup = item.getMuscleGroup();
                        itemDto.targetCount = item.getTargetCount();
                        itemDto.sortOrder = item.getSortOrder();
                        return itemDto;
                    }).collect(Collectors.toList());
                }

                return dayDto;
            }).collect(Collectors.toList());
        }

        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public List<DayPlanDto> getDays() { return days; }
    public void setDays(List<DayPlanDto> days) { this.days = days; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
