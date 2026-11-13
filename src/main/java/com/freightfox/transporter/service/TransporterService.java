package com.freightfox.transporter.service;

import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.response.AssignmentResponse;
import com.freightfox.transporter.dto.response.InputResponse;

/**
 * Service interface for transporter assignment operations.
 */
public interface TransporterService {

    /**
     * Validates and saves the input data (lanes, transporters, quotes) as an atomic replacement.
     * The entire existing dataset is replaced within a single transaction.
     *
     * @param request the input data
     * @return summary of what was saved
     * @throws IllegalArgumentException if validation fails
     */
    InputResponse saveInputData(InputRequest request);

    /**
     * Generates the optimized transporter-to-lane assignment using bitmask DP.
     *
     * @param maxTransporters maximum distinct transporters to use
     * @return the optimized assignment result
     * @throws com.freightfox.transporter.exception.InputDataNotFoundException if no input data exists
     * @throws com.freightfox.transporter.exception.NoFeasibleAssignmentException if no valid assignment exists
     * @throws com.freightfox.transporter.exception.TransporterLimitExceededException if T > 20
     */
    AssignmentResponse generateAssignment(int maxTransporters);
}

