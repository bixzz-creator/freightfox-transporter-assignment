package com.freightfox.transporter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO for a transporter in the input request, including its lane quotations.
 */
public class TransporterDto {

    @NotNull(message = "Transporter ID must not be null")
    private Long id;

    @NotBlank(message = "Transporter name must not be blank")
    private String name;

    @NotEmpty(message = "Lane quotes must not be empty")
    @Valid
    private List<LaneQuoteDto> laneQuotes;

    public TransporterDto() {
    }

    public TransporterDto(Long id, String name, List<LaneQuoteDto> laneQuotes) {
        this.id = id;
        this.name = name;
        this.laneQuotes = laneQuotes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<LaneQuoteDto> getLaneQuotes() {
        return laneQuotes;
    }

    public void setLaneQuotes(List<LaneQuoteDto> laneQuotes) {
        this.laneQuotes = laneQuotes;
    }
}

