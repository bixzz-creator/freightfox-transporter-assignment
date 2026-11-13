package com.freightfox.transporter.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a transportation lane (route) between an origin and destination city.
 */
@Entity
@Table(name = "lane", indexes = {
        @Index(name = "idx_lane_origin_destination", columnList = "origin, destination")
})
public class Lane {

    @Id
    private Long id;

    @Column(nullable = false, length = 255)
    private String origin;

    @Column(nullable = false, length = 255)
    private String destination;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Lane() {
    }

    public Lane(Long id, String origin, String destination) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

