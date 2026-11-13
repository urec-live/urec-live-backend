package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "exercise_notes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "exercise_id"}))
public class ExerciseNote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;
    @Column(nullable = false, length = 2000)
    private String note = "";

    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long value) { exerciseId = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
}
