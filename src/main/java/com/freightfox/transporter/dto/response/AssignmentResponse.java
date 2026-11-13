package com.freightfox.transporter.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response containing the optimized transporter-to-lane assignment result.
 */
public class AssignmentResponse {

    private String status;
    private BigDecimal totalCost;
    private List<SelectedTransporterDto> selectedTransporters;
    private List<AssignmentDetailDto> assignments;
    private int totalLanes;
    private int assignedLanes;
    private int transporterCount;

    public AssignmentResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public List<SelectedTransporterDto> getSelectedTransporters() {
        return selectedTransporters;
    }

    public void setSelectedTransporters(List<SelectedTransporterDto> selectedTransporters) {
        this.selectedTransporters = selectedTransporters;
    }

    public List<AssignmentDetailDto> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<AssignmentDetailDto> assignments) {
        this.assignments = assignments;
    }

    public int getTotalLanes() {
        return totalLanes;
    }

    public void setTotalLanes(int totalLanes) {
        this.totalLanes = totalLanes;
    }

    public int getAssignedLanes() {
        return assignedLanes;
    }

    public void setAssignedLanes(int assignedLanes) {
        this.assignedLanes = assignedLanes;
    }

    public int getTransporterCount() {
        return transporterCount;
    }

    public void setTransporterCount(int transporterCount) {
        this.transporterCount = transporterCount;
    }
}

