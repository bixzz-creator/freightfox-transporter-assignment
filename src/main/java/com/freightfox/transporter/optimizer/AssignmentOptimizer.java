package com.freightfox.transporter.optimizer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface for the transporter assignment optimization algorithm.
 * Implementations must assign every lane to exactly one eligible transporter.
 */
public interface AssignmentOptimizer {

    /**
     * Computes the optimal lane-to-transporter assignment.
     *
     * @param laneIds          ordered list of lane IDs
     * @param transporterIds   ordered list of transporter IDs (sorted ascending for deterministic bit assignment)
     * @param quotes           quotes[laneIdx][transporterIdx] — null if no quote exists
     * @param maxTransporters  maximum number of distinct transporters allowed
     * @return the optimal assignment result
     * @throws com.freightfox.transporter.exception.NoFeasibleAssignmentException if no valid assignment exists
     */
    OptimizationResult optimize(List<Long> laneIds, List<Long> transporterIds,
                                BigDecimal[][] quotes, int maxTransporters);
}

