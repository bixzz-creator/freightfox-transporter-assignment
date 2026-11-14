package com.freightfox.transporter.dto.response;

import com.freightfox.transporter.dto.request.LaneDto;
import com.freightfox.transporter.dto.request.TransporterDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO returning the currently active dataset persisted in the database.
 */
public class CurrentDatasetResponse {

    private String status; // "LOADED" or "EMPTY"
    private int lanesCount;
    private int transportersCount;
    private int quotesCount;
    private BigDecimal averageQuote;
    private BigDecimal minQuote;
    private BigDecimal maxQuote;
    private List<LaneDto> lanes;
    private List<TransporterDto> transporters;
    private String message;

    public CurrentDatasetResponse() {
    }

    public CurrentDatasetResponse(String status, int lanesCount, int transportersCount, int quotesCount,
                                  BigDecimal averageQuote, BigDecimal minQuote, BigDecimal maxQuote,
                                  List<LaneDto> lanes, List<TransporterDto> transporters, String message) {
        this.status = status;
        this.lanesCount = lanesCount;
        this.transportersCount = transportersCount;
        this.quotesCount = quotesCount;
        this.averageQuote = averageQuote;
        this.minQuote = minQuote;
        this.maxQuote = maxQuote;
        this.lanes = lanes;
        this.transporters = transporters;
        this.message = message;
    }

    public static CurrentDatasetResponse empty() {
        return new CurrentDatasetResponse(
                "EMPTY", 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(),
                "No tender dataset currently loaded in database."
        );
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLanesCount() {
        return lanesCount;
    }

    public void setLanesCount(int lanesCount) {
        this.lanesCount = lanesCount;
    }

    public int getTransportersCount() {
        return transportersCount;
    }

    public void setTransportersCount(int transportersCount) {
        this.transportersCount = transportersCount;
    }

    public int getQuotesCount() {
        return quotesCount;
    }

    public void setQuotesCount(int quotesCount) {
        this.quotesCount = quotesCount;
    }

    public BigDecimal getAverageQuote() {
        return averageQuote;
    }

    public void setAverageQuote(BigDecimal averageQuote) {
        this.averageQuote = averageQuote;
    }

    public BigDecimal getMinQuote() {
        return minQuote;
    }

    public void setMinQuote(BigDecimal minQuote) {
        this.minQuote = minQuote;
    }

    public BigDecimal getMaxQuote() {
        return maxQuote;
    }

    public void setMaxQuote(BigDecimal maxQuote) {
        this.maxQuote = maxQuote;
    }

    public List<LaneDto> getLanes() {
        return lanes;
    }

    public void setLanes(List<LaneDto> lanes) {
        this.lanes = lanes;
    }

    public List<TransporterDto> getTransporters() {
        return transporters;
    }

    public void setTransporters(List<TransporterDto> transporters) {
        this.transporters = transporters;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

