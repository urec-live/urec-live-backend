package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "day_plans", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"plan_id", "day_of_week"})
})
public class DayPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private WorkoutPlan plan;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 1=Monday, 7=Sunday (ISO)

    @Column(length = 100)
    private String label; // optional, e.g. "Push Day"

    @OneToMany(mappedBy = "dayPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    private List<DayPlanItem> items = new ArrayList<>();

    public DayPlan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public WorkoutPlan getPlan() { return plan; }
    public void setPlan(WorkoutPlan plan) { this.plan = plan; }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public List<DayPlanItem> getItems() { return items; }
    public void setItems(List<DayPlanItem> items) { this.items = items; }
}
