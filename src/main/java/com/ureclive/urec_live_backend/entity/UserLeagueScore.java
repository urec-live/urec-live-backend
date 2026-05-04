package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_league_scores",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "week_start"}))
public class UserLeagueScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private Double weeklyScore = 0.0;

    @Column(nullable = false, length = 10)
    private String tier;

    @Column(name = "rank_in_tier")
    private Integer rankInTier;

    @Column(nullable = false)
    private Instant computedAt;

    @PrePersist
    @PreUpdate
    void onSave() { this.computedAt = Instant.now(); }

    public UserLeagueScore() {}

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public Double getWeeklyScore() { return weeklyScore; }
    public void setWeeklyScore(Double weeklyScore) { this.weeklyScore = weeklyScore; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public Integer getRankInTier() { return rankInTier; }
    public void setRankInTier(Integer rankInTier) { this.rankInTier = rankInTier; }
    public Instant getComputedAt() { return computedAt; }
}
