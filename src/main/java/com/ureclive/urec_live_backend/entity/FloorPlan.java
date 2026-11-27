package com.ureclive.urec_live_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "floor_plans")
public class FloorPlan {
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private com.ureclive.urec_live_backend.dto.FloorLayout layout;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean illustrative = false;

    public com.ureclive.urec_live_backend.dto.FloorLayout getLayout() {
        return layout == null ? com.ureclive.urec_live_backend.dto.FloorLayout.empty() : layout;
    }
    public void setLayout(com.ureclive.urec_live_backend.dto.FloorLayout value) { layout = value; }
    public boolean isIllustrative() { return illustrative; }
    public void setIllustrative(boolean value) { illustrative = value; }

    private Long gymId;

    public Long getGymId() {
        return gymId;
    }

    public void setGymId(Long value) {
        gymId = value;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Integer width = 800;

    @Column(nullable = false)
    private Integer height = 600;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    public FloorPlan() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(Integer floorNumber) {
        this.floorNumber = floorNumber;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
