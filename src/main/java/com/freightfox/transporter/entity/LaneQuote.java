package com.freightfox.transporter.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a price quotation from a transporter for a specific lane.
 * The combination of lane and transporter must be unique.
 */
@Entity
@Table(name = "lane_quote", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lane_transporter", columnNames = {"lane_id", "transporter_id"})
}, indexes = {
        @Index(name = "idx_lane_quote_lane_id", columnList = "lane_id"),
        @Index(name = "idx_lane_quote_transporter_id", columnList = "transporter_id")
})
public class LaneQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lane_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lane_quote_lane"))
    private Lane lane;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lane_quote_transporter"))
    private Transporter transporter;

    @Column(name = "quote_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal quoteAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LaneQuote() {
    }

    public LaneQuote(Lane lane, Transporter transporter, BigDecimal quoteAmount) {
        this.lane = lane;
        this.transporter = transporter;
        this.quoteAmount = quoteAmount;
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

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public Transporter getTransporter() {
        return transporter;
    }

    public void setTransporter(Transporter transporter) {
        this.transporter = transporter;
    }

    public BigDecimal getQuoteAmount() {
        return quoteAmount;
    }

    public void setQuoteAmount(BigDecimal quoteAmount) {
        this.quoteAmount = quoteAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

