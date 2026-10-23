package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_badges",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "badge_type"}))
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "badge_type", nullable = false, length = 20)
    private String badgeType;

    @Column(nullable = false)
    private Instant awardedAt;

    @PrePersist
    void onCreate() { this.awardedAt = Instant.now(); }

    public UserBadge() {}

    public UserBadge(User user, String badgeType) {
        this.user = user;
        this.badgeType = badgeType;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getBadgeType() { return badgeType; }
    public void setBadgeType(String badgeType) { this.badgeType = badgeType; }
    public Instant getAwardedAt() { return awardedAt; }
}
