package com.freightfox.transporter.dto.response;

import java.math.BigDecimal;

/**
 * DTO representing a single lane-to-transporter assignment in the result.
 */
public class AssignmentDetailDto {

    private Long laneId;
    private String origin;
    private String destination;
    private Long transporterId;
    private String transporterName;
    private BigDecimal assignedQuote;

    public AssignmentDetailDto() {
    }

    public AssignmentDetailDto(Long laneId, String origin, String destination,
                               Long transporterId, String transporterName, BigDecimal assignedQuote) {
        this.laneId = laneId;
        this.origin = origin;
        this.destination = destination;
        this.transporterId = transporterId;
        this.transporterName = transporterName;
        this.assignedQuote = assignedQuote;
    }

    public Long getLaneId() {
        return laneId;
    }

    public void setLaneId(Long laneId) {
        this.laneId = laneId;
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

    public Long getTransporterId() {
        return transporterId;
    }

    public void setTransporterId(Long transporterId) {
        this.transporterId = transporterId;
    }

    public String getTransporterName() {
        return transporterName;
    }

    public void setTransporterName(String transporterName) {
        this.transporterName = transporterName;
    }

    public BigDecimal getAssignedQuote() {
        return assignedQuote;
    }

    public void setAssignedQuote(BigDecimal assignedQuote) {
        this.assignedQuote = assignedQuote;
    }
}

