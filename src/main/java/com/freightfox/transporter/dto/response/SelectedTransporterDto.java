package com.freightfox.transporter.dto.response;

/**
 * DTO representing a transporter selected in the optimized assignment.
 */
public class SelectedTransporterDto {

    private Long transporterId;
    private String transporterName;

    public SelectedTransporterDto() {
    }

    public SelectedTransporterDto(Long transporterId, String transporterName) {
        this.transporterId = transporterId;
        this.transporterName = transporterName;
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
}

