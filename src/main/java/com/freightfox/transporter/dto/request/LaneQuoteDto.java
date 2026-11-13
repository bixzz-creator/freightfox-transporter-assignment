package com.freightfox.transporter.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO for a transporter's price quotation for a specific lane.
 */
public class LaneQuoteDto {

    @NotNull(message = "Lane ID must not be null")
    private Long laneId;

    @NotNull(message = "Quote must not be null")
    @DecimalMin(value = "0.01", message = "Quote must be positive (greater than zero)")
    private BigDecimal quote;

    public LaneQuoteDto() {
    }

    public LaneQuoteDto(Long laneId, BigDecimal quote) {
        this.laneId = laneId;
        this.quote = quote;
    }

    public Long getLaneId() {
        return laneId;
    }

    public void setLaneId(Long laneId) {
        this.laneId = laneId;
    }

    public BigDecimal getQuote() {
        return quote;
    }

    public void setQuote(BigDecimal quote) {
        this.quote = quote;
    }
}

