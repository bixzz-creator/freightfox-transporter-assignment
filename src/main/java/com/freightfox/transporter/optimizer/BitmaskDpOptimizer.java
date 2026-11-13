package com.freightfox.transporter.optimizer;

import com.freightfox.transporter.exception.NoFeasibleAssignmentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Bitmask Dynamic Programming optimizer for transporter-lane assignment.
 *
 * <h3>Lexicographic Objective</h3>
 * <ol>
 *   <li>Maximize the number of distinct transporters <em>actually used</em> (up to maxTransporters).</li>
 *   <li>Minimize total cost among solutions with the same transporter count.</li>
 *   <li>Deterministic tie-breaking:
 *       <ul>
 *         <li>Prefer the solution whose selected transporter IDs are lexicographically smaller.</li>
 *         <li>If still tied, compare lane assignments in lane-index order (prefer the lower transporter ID).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Memory-Optimized Design</h3>
 * <ul>
 *   <li><b>Rolling cost arrays</b>: Only two arrays of size 2^T are kept for the DP cost,
 *       swapped after each lane. No full dp[L][2^T] cost matrix is allocated.</li>
 *   <li><b>Reconstruction arrays</b>: {@code choiceTransporter[lane][mask]} stores the transporter
 *       index chosen, and {@code choicePrevMask[lane][mask]} stores the previous mask.
 *       These are int arrays (4 bytes each) vs BigDecimal, saving significant memory.</li>
 * </ul>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li>Time:  O(L × 2^T × T) where L = lanes, T = transporters</li>
 *   <li>Space: O(L × 2^T) for reconstruction + O(2^T) for rolling cost</li>
 * </ul>
 *
 * <p>Precondition: {@code transporterIds} must be sorted ascending so that bit 0 = lowest ID,
 * bit 1 = next lowest, etc. This ensures deterministic tie-breaking.</p>
 */
@Component
public class BitmaskDpOptimizer implements AssignmentOptimizer {

    private static final Logger log = LoggerFactory.getLogger(BitmaskDpOptimizer.class);

    /**
     * Sentinel for unreachable DP states. Chosen large enough that no real total
     * cost can reach it, but small enough to avoid BigDecimal overflow issues.
     */
    private static final BigDecimal INFINITY = new BigDecimal("999999999999999");

    @Override
    public OptimizationResult optimize(List<Long> laneIds, List<Long> transporterIds,
                                       BigDecimal[][] quotes, int maxTransporters) {

        if (laneIds == null || laneIds.isEmpty()) {
            throw new IllegalArgumentException("laneIds must not be null or empty");
        }
        if (transporterIds == null || transporterIds.isEmpty()) {
            throw new IllegalArgumentException("transporterIds must not be null or empty");
        }
        if (quotes == null || quotes.length != laneIds.size()) {
            throw new IllegalArgumentException("quotes matrix must match lane count");
        }
        if (maxTransporters <= 0) {
            throw new IllegalArgumentException("maxTransporters must be at least 1, received: " + maxTransporters);
        }

        int L = laneIds.size();
        int T = transporterIds.size();
        int totalMasks = 1 << T;

        log.info("Starting Bitmask DP: {} lanes, {} transporters, maxTransporters={}, masks={}",
                L, T, maxTransporters, totalMasks);

        // --- Rolling cost arrays (memory optimization: only 2 × 2^T instead of L × 2^T) ---
        BigDecimal[] prev = new BigDecimal[totalMasks];
        BigDecimal[] curr = new BigDecimal[totalMasks];
        Arrays.fill(prev, INFINITY);
        prev[0] = BigDecimal.ZERO; // Base case: zero lanes assigned, zero cost, empty mask

        // --- Reconstruction arrays: int is much smaller than BigDecimal ---
        // choiceTransporter[lane][mask] = index of the transporter assigned at this lane to reach this mask
        // choicePrevMask[lane][mask]    = the mask before this lane's assignment
        int[][] choiceTransporter = new int[L][totalMasks];
        int[][] choicePrevMask = new int[L][totalMasks];
        for (int lane = 0; lane < L; lane++) {
            Arrays.fill(choiceTransporter[lane], -1);
            Arrays.fill(choicePrevMask[lane], -1);
        }

        // --- Forward DP pass ---
        for (int lane = 0; lane < L; lane++) {
            Arrays.fill(curr, INFINITY);

            for (int mask = 0; mask < totalMasks; mask++) {
                if (prev[mask].compareTo(INFINITY) >= 0) {
                    continue; // Unreachable state — skip
                }

                for (int t = 0; t < T; t++) {
                    // Only consider transporters that have submitted a quote for this lane
                    if (quotes[lane][t] == null) {
                        continue;
                    }

                    int newMask = mask | (1 << t);

                    // Enforce the maxTransporters constraint
                    if (Integer.bitCount(newMask) > maxTransporters) {
                        continue;
                    }

                    BigDecimal newCost = prev[mask].add(quotes[lane][t]);

                    // Determine whether to update:
                    // 1. Lower cost strictly wins.
                    // 2. On equal cost: apply deterministic tie-breaking comparing lane assignments
                    //    in lane-index order (prefer lower transporter ID).
                    boolean shouldUpdate = false;
                    int cmp = newCost.compareTo(curr[newMask]);
                    if (cmp < 0) {
                        shouldUpdate = true;
                    } else if (cmp == 0) {
                        if (choiceTransporter[lane][newMask] == -1) {
                            shouldUpdate = true;
                        } else {
                            shouldUpdate = isLexicographicallySmallerPath(
                                    lane,
                                    choicePrevMask[lane][newMask], choiceTransporter[lane][newMask],
                                    mask, t,
                                    choiceTransporter, choicePrevMask
                            );
                        }
                    }

                    if (shouldUpdate) {
                        curr[newMask] = newCost;
                        choiceTransporter[lane][newMask] = t;
                        choicePrevMask[lane][newMask] = mask;
                    }
                }
            }

            // Swap rolling arrays — prev becomes curr for the next lane
            BigDecimal[] temp = prev;
            prev = curr;
            curr = temp;
        }

        // --- Select the best final mask (lexicographic objective) ---
        // After processing all L lanes, prev[mask] = minimum cost to assign all lanes using exactly
        // the transporters indicated by 'mask'.
        int bestMask = -1;
        BigDecimal bestCost = INFINITY;
        int bestCount = 0;

        for (int mask = 0; mask < totalMasks; mask++) {
            if (prev[mask].compareTo(INFINITY) >= 0) {
                continue; // Unreachable
            }

            int count = Integer.bitCount(mask);
            BigDecimal cost = prev[mask];

            // Lexicographic ordering:
            // 1. Maximize distinct transporter count (up to maxTransporters)
            // 2. Minimize total cost among solutions with same transporter count
            // 3. Deterministic tie-breaking: prefer lexicographically smaller set of transporter IDs
            boolean isBetter = false;
            if (count > bestCount) {
                isBetter = true;
            } else if (count == bestCount) {
                int costCmp = cost.compareTo(bestCost);
                if (costCmp < 0) {
                    isBetter = true;
                } else if (costCmp == 0) {
                    if (bestMask == -1 || compareMasksLexicographically(mask, bestMask) < 0) {
                        isBetter = true;
                    }
                }
            }

            if (isBetter) {
                bestMask = mask;
                bestCost = cost;
                bestCount = count;
            }
        }

        if (bestMask == -1) {
            throw new NoFeasibleAssignmentException(
                    "No feasible assignment exists. Some lanes may have no eligible transporter quotes, " +
                    "or the maxTransporters constraint is too restrictive.");
        }

        log.info("DP complete. Best solution: {} distinct transporters, totalCost={}, mask={}",
                bestCount, bestCost, Integer.toBinaryString(bestMask));

        // --- Backward path reconstruction ---
        List<AssignmentCandidate> assignments = new ArrayList<>(L);
        int currentMask = bestMask;

        for (int lane = L - 1; lane >= 0; lane--) {
            int t = choiceTransporter[lane][currentMask];
            if (t == -1) {
                throw new NoFeasibleAssignmentException(
                        "Path reconstruction failed at lane index " + lane +
                        ". This indicates a data inconsistency.");
            }

            assignments.add(new AssignmentCandidate(
                    lane, t, laneIds.get(lane), transporterIds.get(t), quotes[lane][t]
            ));

            // Move to the previous mask (before this lane was assigned)
            currentMask = choicePrevMask[lane][currentMask];
        }

        // Reverse to get assignments in lane order (we built them backward)
        Collections.reverse(assignments);

        // Collect the set of actually-used transporter IDs (only those with >= 1 assignment)
        Set<Long> usedTransporterIds = new LinkedHashSet<>();
        for (AssignmentCandidate a : assignments) {
            usedTransporterIds.add(a.getTransporterId());
        }

        log.info("Assignment reconstructed: {} lanes -> {} transporters, totalCost={}",
                assignments.size(), usedTransporterIds.size(), bestCost);

        return new OptimizationResult(assignments, bestCost, usedTransporterIds);
    }

    /**
     * Compares two candidate paths reaching state (lane, newMask) that have identical total cost.
     * Returns true if candidate2 (transition from newPrevMask choosing newTransporter) is
     * strictly lexicographically smaller than candidate1 in lane-index order (lane 0 first).
     */
    private boolean isLexicographicallySmallerPath(
            int lane,
            int existingPrevMask, int existingTransporter,
            int newPrevMask, int newTransporter,
            int[][] choiceTransporter, int[][] choicePrevMask) {

        int[] pathExisting = new int[lane + 1];
        int[] pathNew = new int[lane + 1];

        pathExisting[lane] = existingTransporter;
        int mExisting = existingPrevMask;

        pathNew[lane] = newTransporter;
        int mNew = newPrevMask;

        for (int l = lane - 1; l >= 0; l--) {
            pathExisting[l] = choiceTransporter[l][mExisting];
            mExisting = choicePrevMask[l][mExisting];

            pathNew[l] = choiceTransporter[l][mNew];
            mNew = choicePrevMask[l][mNew];
        }

        // Compare from lane 0 upwards (lane-index order)
        for (int l = 0; l <= lane; l++) {
            if (pathNew[l] != pathExisting[l]) {
                return pathNew[l] < pathExisting[l];
            }
        }

        return false; // Exactly identical paths
    }

    /**
     * Compares two transporter masks that have identical transporter count (popcount).
     * Returns negative if maskA is lexicographically smaller than maskB (i.e. uses smaller
     * transporter IDs in sorted order), positive if maskB is smaller, 0 if identical.
     *
     * <p>Because bit 0 corresponds to the lowest transporter ID, bit 1 to the next, etc.,
     * the lowest differing bit determines which mask has the smaller transporter ID.</p>
     */
    private int compareMasksLexicographically(int maskA, int maskB) {
        int diff = maskA ^ maskB;
        if (diff == 0) {
            return 0;
        }
        int lowestDiffBit = Integer.numberOfTrailingZeros(diff);
        return ((maskA & (1 << lowestDiffBit)) != 0) ? -1 : 1;
    }
}
