package com.freightfox.transporter.optimizer;

import com.freightfox.transporter.exception.NoFeasibleAssignmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bitmask DP Optimizer Unit Tests")
class BitmaskDpOptimizerTest {

    private BitmaskDpOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new BitmaskDpOptimizer();
    }

    @Test
    @DisplayName("1. Single lane and single eligible transporter")
    void testSingleLaneSingleTransporter() {
        List<Long> laneIds = List.of(1L);
        List<Long> transporterIds = List.of(101L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("5000.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 1);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.getTotalCost()));
        assertEquals(1, result.getUsedTransporterIds().size());
        assertTrue(result.getUsedTransporterIds().contains(101L));
        assertEquals(1, result.getAssignments().size());
        assertEquals(1L, result.getAssignments().get(0).getLaneId());
        assertEquals(101L, result.getAssignments().get(0).getTransporterId());
    }

    @Test
    @DisplayName("2. Multiple lanes assigned to the same transporter when maxTransporters = 1")
    void testMultipleLanesSingleTransporter() {
        List<Long> laneIds = List.of(1L, 2L, 3L);
        List<Long> transporterIds = List.of(101L, 102L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("1000.00"), new BigDecimal("1500.00")},
                {new BigDecimal("2000.00"), new BigDecimal("2500.00")},
                {new BigDecimal("3000.00"), new BigDecimal("3500.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 1);

        assertNotNull(result);
        // T101 is cheaper for all 3 lanes (1000+2000+3000 = 6000 vs 1500+2500+3500 = 7500)
        assertEquals(0, new BigDecimal("6000.00").compareTo(result.getTotalCost()));
        assertEquals(1, result.getUsedTransporterIds().size());
        assertTrue(result.getUsedTransporterIds().contains(101L));
        assertEquals(3, result.getAssignments().size());
        for (AssignmentCandidate c : result.getAssignments()) {
            assertEquals(101L, c.getTransporterId());
        }
    }

    @Test
    @DisplayName("3. Multiple transporters required for complete lane coverage (complementary quotes)")
    void testMultipleTransportersRequiredForCoverage() {
        List<Long> laneIds = List.of(1L, 2L);
        List<Long> transporterIds = List.of(101L, 102L);
        // T101 only quoted lane 1, T102 only quoted lane 2
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("5000.00"), null},
                {null, new BigDecimal("4000.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 2);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("9000.00").compareTo(result.getTotalCost()));
        assertEquals(2, result.getUsedTransporterIds().size());
        assertTrue(result.getUsedTransporterIds().containsAll(List.of(101L, 102L)));
        assertEquals(101L, result.getAssignments().get(0).getTransporterId());
        assertEquals(102L, result.getAssignments().get(1).getTransporterId());
    }

    @Test
    @DisplayName("4. Maximum transporter usage constraint is strictly respected")
    void testMaxTransporterLimitRespected() {
        List<Long> laneIds = List.of(1L, 2L, 3L);
        List<Long> transporterIds = List.of(101L, 102L, 103L);
        // Each transporter has lowest quote on a different lane
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("500.00")},
                {new BigDecimal("500.00"), new BigDecimal("100.00"), new BigDecimal("500.00")},
                {new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("100.00")}
        };

        // If maxTransporters = 2, it cannot use all 3 despite being cheaper (100+100+100=300)
        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 2);

        assertNotNull(result);
        assertTrue(result.getUsedTransporterIds().size() <= 2,
                "Must not exceed maxTransporters=2, but was " + result.getUsedTransporterIds().size());
        assertEquals(2, result.getUsedTransporterIds().size());
    }

    @Test
    @DisplayName("5b. Explicit lexicographic test: higher cost with more transporters is chosen over lower cost with fewer transporters")
    void testExplicitLexicographicHigherCostWithMoreTransporters() {
        // 3 lanes, 3 transporters
        List<Long> laneIds = List.of(1L, 2L, 3L);
        List<Long> transporterIds = List.of(101L, 102L, 103L);

        // Transporter 101 can serve all 3 lanes very cheaply (100 each -> total 300)
        // Transporters 102 and 103 offer quotes that are significantly more expensive
        // Solution using 1 transporter (T101 only): cost = 100 + 100 + 100 = 300
        // Solution using 2 transporters (T101, T102): e.g. L1=T101(100), L2=T102(200), L3=T101(100) -> cost = 400
        // Solution using 3 transporters (T101, T102, T103): L1=T101(250), L2=T102(250), L3=T103(250) -> cost = 750
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("300.00"), new BigDecimal("250.00")},
                {new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("300.00")},
                {new BigDecimal("100.00"), new BigDecimal("400.00"), new BigDecimal("250.00")}
        };

        // When maxTransporters = 3:
        // Even though 1 transporter costs only 300, the lexicographic objective demands maximizing
        // distinct transporters up to 3. A 3-transporter solution exists (cost 100+200+250 = 550).
        // It MUST pick the 3-transporter solution over the cheaper 1-transporter solution.
        OptimizationResult resultMax3 = optimizer.optimize(laneIds, transporterIds, quotes, 3);
        assertNotNull(resultMax3);
        assertEquals(3, resultMax3.getUsedTransporterIds().size(),
                "Must select 3 transporters even if total cost is higher than 1-transporter solution");
        assertTrue(resultMax3.getUsedTransporterIds().containsAll(List.of(101L, 102L, 103L)));
        assertTrue(resultMax3.getTotalCost().compareTo(new BigDecimal("300.00")) > 0,
                "Cost of 3-transporter solution must be higher than the 1-transporter alternative (300)");

        // When maxTransporters = 2:
        // It must pick the 2-transporter solution over the cheaper 1-transporter solution.
        OptimizationResult resultMax2 = optimizer.optimize(laneIds, transporterIds, quotes, 2);
        assertNotNull(resultMax2);
        assertEquals(2, resultMax2.getUsedTransporterIds().size(),
                "Must select 2 transporters over 1 transporter despite higher cost");
        assertTrue(resultMax2.getTotalCost().compareTo(new BigDecimal("300.00")) > 0);

        // When maxTransporters = 1:
        // Constrained to at most 1 transporter, so it picks T101 with the lowest cost of 300.
        OptimizationResult resultMax1 = optimizer.optimize(laneIds, transporterIds, quotes, 1);
        assertNotNull(resultMax1);
        assertEquals(1, resultMax1.getUsedTransporterIds().size());
        assertTrue(resultMax1.getUsedTransporterIds().contains(101L));
        assertEquals(0, new BigDecimal("300.00").compareTo(resultMax1.getTotalCost()));
    }
    void testLexicographicObjectiveMaxTransporterCountFirst() {
        List<Long> laneIds = List.of(1L, 2L);
        List<Long> transporterIds = List.of(101L, 102L);

        // Solution with 1 transporter (T101): cost = 10 + 10 = 20
        // Solution with 2 transporters (T101 for L1, T102 for L2): cost = 10 + 50 = 60
        // If maxTransporters = 2:
        // Lexicographic ordering:
        // 1. Maximize distinct transporters (2 > 1) -> Solution with 2 transporters MUST be chosen!
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("10.00"), new BigDecimal("100.00")},
                {new BigDecimal("10.00"), new BigDecimal("50.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 2);

        assertNotNull(result);
        assertEquals(2, result.getUsedTransporterIds().size(),
                "Objective requires maximizing distinct transporters up to maxTransporters (2)");
        assertTrue(result.getUsedTransporterIds().containsAll(List.of(101L, 102L)));
        assertEquals(0, new BigDecimal("60.00").compareTo(result.getTotalCost()));
    }

    @Test
    @DisplayName("6. Lowest total cost among solutions with the same transporter count")
    void testLowestCostAmongSameTransporterCount() {
        List<Long> laneIds = List.of(1L, 2L);
        List<Long> transporterIds = List.of(101L, 102L, 103L);

        // Suppose maxTransporters = 2.
        // Pair {T101, T102}: lane1=T101 (100), lane2=T102 (200) -> 300
        // Pair {T101, T103}: lane1=T101 (100), lane2=T103 (150) -> 250 (cheaper!)
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("500.00")},
                {new BigDecimal("500.00"), new BigDecimal("200.00"), new BigDecimal("150.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 2);

        assertNotNull(result);
        assertEquals(2, result.getUsedTransporterIds().size());
        assertTrue(result.getUsedTransporterIds().containsAll(List.of(101L, 103L)));
        assertEquals(0, new BigDecimal("250.00").compareTo(result.getTotalCost()));
    }

    @Test
    @DisplayName("7. Deterministic tie-breaking for equal-cost alternatives")
    void testDeterministicTieBreaking() {
        List<Long> laneIds = List.of(1L, 2L);
        List<Long> transporterIds = List.of(101L, 102L);

        // Both transporters offer identical quotes
        // With maxTransporters = 1:
        // T101 cost = 100 + 100 = 200
        // T102 cost = 100 + 100 = 200
        // Tie-breaker prefers lower transporter ID (101L)
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("100.00")},
                {new BigDecimal("100.00"), new BigDecimal("100.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 1);

        assertNotNull(result);
        assertEquals(1, result.getUsedTransporterIds().size());
        assertTrue(result.getUsedTransporterIds().contains(101L),
                "Tie-break should deterministically choose lower transporter ID 101 over 102");
        assertEquals(101L, result.getAssignments().get(0).getTransporterId());
        assertEquals(101L, result.getAssignments().get(1).getTransporterId());
    }

    @Test
    @DisplayName("8. Missing transporter quotes: transporter with null quote is never assigned")
    void testMissingTransporterQuotes() {
        List<Long> laneIds = List.of(1L, 2L, 3L);
        List<Long> transporterIds = List.of(101L, 102L);

        // T101 quoted lanes 1 and 2, but NOT lane 3
        // T102 quoted all lanes
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("200.00")},
                {new BigDecimal("100.00"), new BigDecimal("200.00")},
                {null, new BigDecimal("300.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 2);

        assertNotNull(result);
        assertEquals(3, result.getAssignments().size());
        // Lane 3 MUST be assigned to T102 because T101 has no quote
        assertEquals(102L, result.getAssignments().get(2).getTransporterId());
    }

    @Test
    @DisplayName("9. No feasible assignment when a lane has no quotes from any transporter")
    void testNoFeasibleAssignmentLaneHasNoQuotes() {
        List<Long> laneIds = List.of(1L, 2L);
        List<Long> transporterIds = List.of(101L, 102L);

        // Lane 2 has no quotes from anyone
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("200.00")},
                {null, null}
        };

        assertThrows(NoFeasibleAssignmentException.class, () ->
                optimizer.optimize(laneIds, transporterIds, quotes, 2));
    }

    @Test
    @DisplayName("10. maxTransporters greater than available transporter count")
    void testMaxTransportersGreaterThanAvailable() {
        List<Long> laneIds = List.of(1L, 2L);
        List<Long> transporterIds = List.of(101L, 102L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("150.00")},
                {new BigDecimal("150.00"), new BigDecimal("100.00")}
        };

        // maxTransporters = 10, but only 2 exist
        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 10);

        assertNotNull(result);
        assertEquals(2, result.getUsedTransporterIds().size());
        assertEquals(0, new BigDecimal("200.00").compareTo(result.getTotalCost()));
    }

    @Test
    @DisplayName("11. maxTransporters = 0 and negative values throw IllegalArgumentException")
    void testInvalidMaxTransporters() {
        List<Long> laneIds = List.of(1L);
        List<Long> transporterIds = List.of(101L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00")}
        };

        assertThrows(IllegalArgumentException.class, () ->
                optimizer.optimize(laneIds, transporterIds, quotes, 0));

        assertThrows(IllegalArgumentException.class, () ->
                optimizer.optimize(laneIds, transporterIds, quotes, -1));
    }

    @Test
    @DisplayName("12. Transporter not included in selectedTransporters unless it receives at least one lane")
    void testOnlyAssignedTransportersInSelectedSet() {
        List<Long> laneIds = List.of(1L);
        List<Long> transporterIds = List.of(101L, 102L, 103L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("300.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 3);

        assertNotNull(result);
        // Only 1 lane exists, so at most 1 transporter can be used
        assertEquals(1, result.getUsedTransporterIds().size());
        assertEquals(1, result.getAssignments().size());
        assertTrue(result.getUsedTransporterIds().contains(result.getAssignments().get(0).getTransporterId()));
    }

    @Test
    @DisplayName("13. Total cost equals the sum of all individual lane assignment quotes")
    void testTotalCostEqualsSumOfAssignments() {
        List<Long> laneIds = List.of(1L, 2L, 3L, 4L);
        List<Long> transporterIds = List.of(101L, 102L, 103L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("1200.50"), new BigDecimal("1100.00"), new BigDecimal("1300.00")},
                {new BigDecimal("2200.00"), new BigDecimal("2150.25"), new BigDecimal("2100.00")},
                {new BigDecimal("3500.00"), new BigDecimal("3400.00"), new BigDecimal("3350.50")},
                {new BigDecimal("4100.75"), new BigDecimal("4200.00"), new BigDecimal("4050.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 3);

        assertNotNull(result);
        BigDecimal sumOfQuotes = result.getAssignments().stream()
                .map(AssignmentCandidate::getQuote)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, sumOfQuotes.compareTo(result.getTotalCost()),
                "Calculated sum (" + sumOfQuotes + ") must match totalCost (" + result.getTotalCost() + ")");
    }

    @Test
    @DisplayName("14. Every lane appears exactly once in assignments in correct order")
    void testEveryLaneAppearsExactlyOnce() {
        List<Long> laneIds = List.of(10L, 20L, 30L, 40L, 50L);
        List<Long> transporterIds = List.of(101L, 102L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("110.00")},
                {new BigDecimal("200.00"), new BigDecimal("190.00")},
                {new BigDecimal("300.00"), new BigDecimal("310.00")},
                {new BigDecimal("400.00"), new BigDecimal("390.00")},
                {new BigDecimal("500.00"), new BigDecimal("510.00")}
        };

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quotes, 2);

        assertNotNull(result);
        assertEquals(laneIds.size(), result.getAssignments().size());
        for (int i = 0; i < laneIds.size(); i++) {
            assertEquals(laneIds.get(i), result.getAssignments().get(i).getLaneId(),
                    "Lane at index " + i + " must match original lane ID");
        }
    }

    @Test
    @DisplayName("15. Repeated executions return identical deterministic results")
    void testRepeatedExecutionsDeterministic() {
        List<Long> laneIds = List.of(1L, 2L, 3L);
        List<Long> transporterIds = List.of(101L, 102L, 103L);
        BigDecimal[][] quotes = new BigDecimal[][]{
                {new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00")},
                {new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("200.00")},
                {new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00")}
        };

        OptimizationResult first = optimizer.optimize(laneIds, transporterIds, quotes, 2);
        for (int run = 0; run < 10; run++) {
            OptimizationResult current = optimizer.optimize(laneIds, transporterIds, quotes, 2);
            assertEquals(0, first.getTotalCost().compareTo(current.getTotalCost()));
            assertEquals(first.getUsedTransporterIds(), current.getUsedTransporterIds());
            for (int i = 0; i < first.getAssignments().size(); i++) {
                assertEquals(first.getAssignments().get(i).getTransporterId(),
                        current.getAssignments().get(i).getTransporterId());
            }
        }
    }
}

