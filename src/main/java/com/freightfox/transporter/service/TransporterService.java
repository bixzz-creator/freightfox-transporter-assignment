package com.freightfox.transporter.service;

import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.response.AssignmentResponse;
import com.freightfox.transporter.dto.response.CurrentDatasetResponse;
import com.freightfox.transporter.dto.response.DeleteResponse;
import com.freightfox.transporter.dto.response.InputResponse;
import com.freightfox.transporter.dto.response.SystemStatusResponse;

import java.util.Optional;

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

    /**
     * Retrieves the currently active dataset loaded in the database.
     *
     * @return current dataset response with lanes, transporters, and quotations
     */
    CurrentDatasetResponse getCurrentDataset();

    /**
     * Atomically clears all lanes, transporters, and quotes from the database,
     * resetting the active tender state.
     *
     * @return confirmation of deletion
     */
    DeleteResponse clearAllData();

    /**
     * Returns dynamic system, engine, database, and operator status.
     *
     * @return system status response
     */
    SystemStatusResponse getSystemStatus();

    /**
     * Returns the latest computed assignment result, if one exists.
     *
     * @return optional containing the latest assignment response, or empty if none
     */
    Optional<AssignmentResponse> getLatestAssignment();
}
