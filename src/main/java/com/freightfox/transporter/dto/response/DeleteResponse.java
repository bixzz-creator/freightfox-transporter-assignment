package com.freightfox.transporter.dto.response;

import java.time.Instant;

/**
 * Response DTO returned after deleting or unloading tender dataset.
 */
public class DeleteResponse {

    private String status;
    private String message;
    private Instant timestamp;

    public DeleteResponse() {
    }

    public DeleteResponse(String status, String message, Instant timestamp) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

