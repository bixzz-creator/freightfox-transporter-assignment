package com.freightfox.transporter.optimizer;

import com.freightfox.transporter.exception.NoFeasibleAssignmentException;

import java.math.BigDecimal;
import java.util.*;

/**
 * An exhaustive brute-force optimizer used exclusively as a test oracle to verify
 * the correctness of the BitmaskDynamicProgrammingOptimizer.
 *
 * <p>It enumerates all possible T^L lane assignments, discards invalid assignments
 * (missing quotes or exceeding maxTransporters), and selects the assignment that
 * satisfies the exact lexicographic objective:
 * <ol>
 *   <li>Maximize distinct transporters actually used (up to maxTransporters).</li>
 *   <li>Minimize total cost among solutions with the same transporter count.</li>
 *   <li>Deterministic tie-breaking:
 *       <ul>
 *         <li>Prefer lexicographically smaller set of selected transporter IDs.</li>
 *         <li>If still tied, compare lane assignments in lane-index order (prefer lower transporter ID).</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public class BruteForceReferenceOptimizer implements AssignmentOptimizer {

    @Override
    public OptimizationResult optimize(List<Long> laneIds, List<Long> transporterIds,
                                       BigDecimal[][] quotes, int maxTransporters) {

        int L = laneIds.size();
        int T = transporterIds.size();

        int[] bestAssignment = null;
        BigDecimal bestCost = null;
        int bestTransporterCount = -1;
        List<Long> bestTransporterIdSet = null;

        int[] currentAssignment = new int[L];
        long totalCombinations = 1;
        for (int i = 0; i < L; i++) {
            totalCombinations *= T;
        }

        for (long combo = 0; combo < totalCombinations; combo++) {
            long temp = combo;
            boolean valid = true;
            BigDecimal currentCost = BigDecimal.ZERO;
            Set<Integer> usedTransporterIndices = new HashSet<>();

            for (int lane = 0; lane < L; lane++) {
                int t = (int) (temp % T);
                temp /= T;
                currentAssignment[lane] = t;

                BigDecimal q = quotes[lane][t];
                if (q == null) {
                    valid = false;
                    break;
                }
                currentCost = currentCost.add(q);
                usedTransporterIndices.add(t);
            }

            if (!valid) {
                continue;
            }

            if (usedTransporterIndices.size() > maxTransporters) {
                continue;
            }

            int count = usedTransporterIndices.size();
            List<Long> currentIdList = usedTransporterIndices.stream()
                    .map(transporterIds::get)
                    .sorted()
                    .toList();

            boolean isBetter = false;
            if (bestAssignment == null) {
                isBetter = true;
            } else if (count > bestTransporterCount) {
                isBetter = true;
            } else if (count == bestTransporterCount) {
                int costCmp = currentCost.compareTo(bestCost);
                if (costCmp < 0) {
                    isBetter = true;
                } else if (costCmp == 0) {
                    // Tie-break 1: compare sorted transporter IDs
                    int listCmp = compareIdLists(currentIdList, bestTransporterIdSet);
                    if (listCmp < 0) {
                        isBetter = true;
                    } else if (listCmp == 0) {
                        // Tie-break 2: compare lane assignments in lane-index order
                        int laneCmp = compareLaneAssignments(currentAssignment, bestAssignment, transporterIds);
                        if (laneCmp < 0) {
                            isBetter = true;
                        }
                    }
                }
            }

            if (isBetter) {
                bestAssignment = currentAssignment.clone();
                bestCost = currentCost;
                bestTransporterCount = count;
                bestTransporterIdSet = currentIdList;
            }
        }

        if (bestAssignment == null) {
            throw new NoFeasibleAssignmentException("No feasible assignment found by brute-force");
        }

        List<AssignmentCandidate> candidates = new ArrayList<>();
        Set<Long> usedIds = new LinkedHashSet<>();
        for (int lane = 0; lane < L; lane++) {
            int t = bestAssignment[lane];
            candidates.add(new AssignmentCandidate(
                    lane, t, laneIds.get(lane), transporterIds.get(t), quotes[lane][t]
            ));
            usedIds.add(transporterIds.get(t));
        }

        return new OptimizationResult(candidates, bestCost, usedIds);
    }

    private int compareIdLists(List<Long> a, List<Long> b) {
        int minSize = Math.min(a.size(), b.size());
        for (int i = 0; i < minSize; i++) {
            int cmp = a.get(i).compareTo(b.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private int compareLaneAssignments(int[] a, int[] b, List<Long> transporterIds) {
        for (int l = 0; l < a.length; l++) {
            Long idA = transporterIds.get(a[l]);
            Long idB = transporterIds.get(b[l]);
            int cmp = idA.compareTo(idB);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}

