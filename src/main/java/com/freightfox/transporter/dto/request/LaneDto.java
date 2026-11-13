package com.freightfox.transporter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for a transportation lane in the input request.
 */
public class LaneDto {

    @NotNull(message = "Lane ID must not be null")
    private Long id;

    @NotBlank(message = "Origin must not be blank")
    private String origin;

    @NotBlank(message = "Destination must not be blank")
    private String destination;

    public LaneDto() {
    }

    public LaneDto(Long id, String origin, String destination) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
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
}

