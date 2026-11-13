package com.freightfox.transporter.optimizer;

import com.freightfox.transporter.exception.NoFeasibleAssignmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Part B: Algorithm Correctness — DP vs Brute-Force Verification")
class OptimizerBruteForceVerificationTest {

    private BitmaskDpOptimizer dpOptimizer;
    private BruteForceReferenceOptimizer bruteForceOptimizer;

    @BeforeEach
    void setUp() {
        dpOptimizer = new BitmaskDpOptimizer();
        bruteForceOptimizer = new BruteForceReferenceOptimizer();
    }

    @Test
    @DisplayName("Verify DP against exhaustive Brute-Force over 50 randomized test instances")
    void testDpAgainstBruteForceRandomized() {
        // Fixed random seed for reproducibility
        Random random = new Random(42L);

        int totalCases = 50;
        int matchedCases = 0;
        int infeasibleCases = 0;

        for (int iteration = 0; iteration < totalCases; iteration++) {
            // Small sizes so brute force T^L completes in milliseconds
            int L = 2 + random.nextInt(4); // 2 to 5 lanes
            int T = 2 + random.nextInt(4); // 2 to 5 transporters
            int maxTransporters = 1 + random.nextInt(T);

            List<Long> laneIds = new ArrayList<>();
            for (long l = 1; l <= L; l++) {
                laneIds.add(l);
            }

            List<Long> transporterIds = new ArrayList<>();
            for (long t = 100; t < 100 + T; t++) {
                transporterIds.add(t);
            }
            Collections.sort(transporterIds);

            // Generate quotes matrix with potential missing quotes (null)
            BigDecimal[][] quotes = new BigDecimal[L][T];
            for (int l = 0; l < L; l++) {
                // Ensure at least one quote per lane so infeasibility is due to constraints or lack of quotes
                int forcedTransporter = random.nextInt(T);
                for (int t = 0; t < T; t++) {
                    if (t == forcedTransporter || random.nextDouble() > 0.3) {
                        // Integer quote from 10 to 100 to avoid floating issues and create ties
                        int amount = 10 + random.nextInt(90);
                        quotes[l][t] = BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY);
                    } else {
                        quotes[l][t] = null; // No quote
                    }
                }
            }

            OptimizationResult dpResult = null;
            OptimizationResult bfResult = null;
            boolean dpThrew = false;
            boolean bfThrew = false;

            try {
                dpResult = dpOptimizer.optimize(laneIds, transporterIds, quotes, maxTransporters);
            } catch (NoFeasibleAssignmentException e) {
                dpThrew = true;
            }

            try {
                bfResult = bruteForceOptimizer.optimize(laneIds, transporterIds, quotes, maxTransporters);
            } catch (NoFeasibleAssignmentException e) {
                bfThrew = true;
            }

            // Both must agree on feasibility
            assertEquals(bfThrew, dpThrew,
                    "Disagreement on feasibility at iteration " + iteration +
                    " (L=" + L + ", T=" + T + ", maxT=" + maxTransporters + ")");

            if (dpThrew) {
                infeasibleCases++;
                continue;
            }

            // 1. All lanes are assigned exactly once
            assertEquals(L, dpResult.getAssignments().size());
            for (int i = 0; i < L; i++) {
                assertEquals(laneIds.get(i), dpResult.getAssignments().get(i).getLaneId());
            }

            // 2. Every assignment corresponds to an existing quote
            for (AssignmentCandidate candidate : dpResult.getAssignments()) {
                int l = candidate.getLaneIndex();
                int t = candidate.getTransporterIndex();
                assertNotNull(quotes[l][t], "Assigned quote must not be null");
                assertEquals(0, quotes[l][t].compareTo(candidate.getQuote()), "Quote amount must match");
            }

            // 3. Distinct assigned transporter count does not exceed maxTransporters
            assertTrue(dpResult.getUsedTransporterIds().size() <= maxTransporters,
                    "Used transporters (" + dpResult.getUsedTransporterIds().size() +
                    ") must not exceed maxTransporters (" + maxTransporters + ")");

            // 4. Lexicographic objective: transporter count matches optimal brute-force
            assertEquals(bfResult.getUsedTransporterIds().size(), dpResult.getUsedTransporterIds().size(),
                    "Transporter count mismatch at iteration " + iteration);

            // 5. Total cost matches optimal brute-force
            assertEquals(0, bfResult.getTotalCost().compareTo(dpResult.getTotalCost()),
                    "Total cost mismatch at iteration " + iteration +
                    ": DP=" + dpResult.getTotalCost() + ", BF=" + bfResult.getTotalCost());

            // 6. Selected transporter IDs match (tie-breaking test)
            assertEquals(bfResult.getUsedTransporterIds(), dpResult.getUsedTransporterIds(),
                    "Selected transporter set mismatch at iteration " + iteration);

            // 7. Individual lane assignments match (consistent tie-breaking)
            for (int i = 0; i < L; i++) {
                assertEquals(bfResult.getAssignments().get(i).getTransporterId(),
                        dpResult.getAssignments().get(i).getTransporterId(),
                        "Assignment for lane " + i + " mismatch at iteration " + iteration);
            }

            matchedCases++;
        }

        assertTrue(matchedCases > 30, "At least 30 feasible cases should have been verified, got " + matchedCases);
        System.out.println("Brute-Force Verification Summary: " + matchedCases + " feasible matched, " +
                infeasibleCases + " infeasible matched out of " + totalCases + " random instances.");
    }
}

