package com.freightfox.transporter.dto.response;

/**
 * Response returned after successfully saving input data.
 */
public class InputResponse {

    private String status;
    private int lanesCount;
    private int transportersCount;
    private int quotesCount;
    private String message;

    public InputResponse() {
    }

    public InputResponse(String status, int lanesCount, int transportersCount, int quotesCount, String message) {
        this.status = status;
        this.lanesCount = lanesCount;
        this.transportersCount = transportersCount;
        this.quotesCount = quotesCount;
        this.message = message;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

