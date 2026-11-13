package com.freightfox.transporter.optimizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The result of running the optimization algorithm.
 * Contains the assignments, total cost, and the set of distinct transporters used.
 */
public class OptimizationResult {

    private final List<AssignmentCandidate> assignments;
    private final BigDecimal totalCost;
    private final Set<Long> usedTransporterIds;

    public OptimizationResult(List<AssignmentCandidate> assignments, BigDecimal totalCost,
                              Set<Long> usedTransporterIds) {
        this.assignments = assignments;
        this.totalCost = totalCost;
        this.usedTransporterIds = usedTransporterIds;
    }

    public List<AssignmentCandidate> getAssignments() {
        return assignments;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public Set<Long> getUsedTransporterIds() {
        return usedTransporterIds;
    }
}

