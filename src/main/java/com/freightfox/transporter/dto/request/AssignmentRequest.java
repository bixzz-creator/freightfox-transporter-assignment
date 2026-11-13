package com.freightfox.transporter.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the assignment optimization API.
 */
public class AssignmentRequest {

    @NotNull(message = "maxTransporters must not be null")
    @Min(value = 1, message = "maxTransporters must be at least 1")
    private Integer maxTransporters;

    public AssignmentRequest() {
    }

    public AssignmentRequest(Integer maxTransporters) {
        this.maxTransporters = maxTransporters;
    }

    public Integer getMaxTransporters() {
        return maxTransporters;
    }

    public void setMaxTransporters(Integer maxTransporters) {
        this.maxTransporters = maxTransporters;
    }
}

