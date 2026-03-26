package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "day_plan_items")
public class DayPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_plan_id", nullable = false)
    private DayPlan dayPlan;

    @Column(nullable = false, length = 50)
    private String muscleGroup;

    @Column(nullable = false)
    private Integer targetCount = 1;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    public DayPlanItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DayPlan getDayPlan() { return dayPlan; }
    public void setDayPlan(DayPlan dayPlan) { this.dayPlan = dayPlan; }

    public String getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
