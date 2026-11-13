package com.freightfox.transporter.optimizer;

import java.math.BigDecimal;

/**
 * Represents a single lane-to-transporter assignment produced by the optimizer.
 */
public class AssignmentCandidate {

    private final int laneIndex;
    private final int transporterIndex;
    private final long laneId;
    private final long transporterId;
    private final BigDecimal quote;

    public AssignmentCandidate(int laneIndex, int transporterIndex,
                               long laneId, long transporterId, BigDecimal quote) {
        this.laneIndex = laneIndex;
        this.transporterIndex = transporterIndex;
        this.laneId = laneId;
        this.transporterId = transporterId;
        this.quote = quote;
    }

    public int getLaneIndex() {
        return laneIndex;
    }

    public int getTransporterIndex() {
        return transporterIndex;
    }

    public long getLaneId() {
        return laneId;
    }

    public long getTransporterId() {
        return transporterId;
    }

    public BigDecimal getQuote() {
        return quote;
    }
}

