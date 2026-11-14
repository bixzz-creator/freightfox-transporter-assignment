package com.freightfox.transporter.controller;

import com.freightfox.transporter.dto.request.AssignmentRequest;
import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.response.*;
import com.freightfox.transporter.service.TransporterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for transporter assignment operations.
 * Handles input data submission and optimized assignment generation.
 */
@RestController
@RequestMapping("/api/v1/transporters")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Transporter Assignment", description = "Endpoints for lane & quotation ingestion and assignment optimization")
public class TransporterController {

    private static final Logger log = LoggerFactory.getLogger(TransporterController.class);

    private final TransporterService transporterService;

    public TransporterController(TransporterService transporterService) {
        this.transporterService = transporterService;
    }

    @PostMapping("/input")
    @Operation(
            summary = "Save lane and transporter input data",
            description = """
                    Accepts transportation lanes, transporter details, and lane quotations.
                    
                    **Atomic Full Replacement:**
                    - The entire payload is pre-validated (no duplicates, no unknown lane references, positive quotes).
                    - If valid, existing records are replaced with the new dataset within a single database transaction.
                    - If any error occurs during validation or persistence, the transaction rolls back, preserving the old data.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Lanes and transporters dataset with price quotations",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = InputRequest.class),
                    examples = @ExampleObject(
                            name = "Standard Logistics Ingestion",
                            value = """
                                    {
                                      "lanes": [
                                        {"id": 1, "origin": "Mumbai", "destination": "Delhi"},
                                        {"id": 2, "origin": "Delhi", "destination": "Bangalore"}
                                      ],
                                      "transporters": [
                                        {
                                          "id": 101,
                                          "name": "ABC Logistics",
                                          "laneQuotes": [
                                            {"laneId": 1, "quote": 5000.00},
                                            {"laneId": 2, "quote": 6000.00}
                                          ]
                                        },
                                        {
                                          "id": 102,
                                          "name": "XYZ Express",
                                          "laneQuotes": [
                                            {"laneId": 1, "quote": 5500.00},
                                            {"laneId": 2, "quote": 5800.00}
                                          ]
                                        }
                                      ]
                                    }
                                    """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Input data successfully validated and persisted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InputResponse.class),
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                            {
                                              "status": "SUCCESS",
                                              "lanesCount": 2,
                                              "transportersCount": 2,
                                              "quotesCount": 4,
                                              "message": "Input data saved successfully. Previous data has been replaced."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error (duplicate IDs, negative quotes, unknown lane references, missing fields)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Duplicate ID Error",
                                    value = """
                                            {
                                              "timestamp": "2026-11-13T12:00:00",
                                              "status": 400,
                                              "error": "Bad Request",
                                              "message": "Duplicate lane ID: 1",
                                              "path": "/api/v1/transporters/input"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<InputResponse> saveInputData(@Valid @RequestBody InputRequest request) {
        log.info("POST /api/v1/transporters/input — {} lanes, {} transporters",
                request.getLanes().size(), request.getTransporters().size());

        InputResponse response = transporterService.saveInputData(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/assignment")
    @Operation(
            summary = "Generate optimized transporter assignment",
            description = """
                    Runs the Bitmask Dynamic Programming optimization algorithm to assign every lane to an eligible transporter.
                    
                    **Strict Lexicographic Objective:**
                    1. **Maximize Distinct Transporters**: Maximizes the number of distinct transporters actually assigned to >= 1 lane (up to `maxTransporters`).
                    2. **Minimize Total Cost**: Among all solutions achieving that maximal transporter count, minimizes total cost.
                    3. **Deterministic Tie-Breaking**: Deterministic comparison by sorted transporter IDs and lane-index order.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Optimization constraints including maxTransporters",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AssignmentRequest.class),
                    examples = @ExampleObject(
                            name = "Assignment Request",
                            value = """
                                    {
                                      "maxTransporters": 2
                                    }
                                    """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Optimal assignment successfully generated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AssignmentResponse.class),
                            examples = @ExampleObject(
                                    name = "Success Assignment",
                                    value = """
                                            {
                                              "status": "SUCCESS",
                                              "totalCost": 10800.00,
                                              "selectedTransporters": [
                                                {"transporterId": 101, "transporterName": "ABC Logistics"},
                                                {"transporterId": 102, "transporterName": "XYZ Express"}
                                              ],
                                              "assignments": [
                                                {
                                                  "laneId": 1,
                                                  "origin": "Mumbai",
                                                  "destination": "Delhi",
                                                  "transporterId": 101,
                                                  "transporterName": "ABC Logistics",
                                                  "assignedQuote": 5000.00
                                                },
                                                {
                                                  "laneId": 2,
                                                  "origin": "Delhi",
                                                  "destination": "Bangalore",
                                                  "transporterId": 102,
                                                  "transporterName": "XYZ Express",
                                                  "assignedQuote": 5800.00
                                                }
                                              ],
                                              "totalLanes": 2,
                                              "assignedLanes": 2,
                                              "transporterCount": 2
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters (e.g. maxTransporters <= 0)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Invalid maxTransporters",
                                    value = """
                                            {
                                              "timestamp": "2026-11-13T12:00:00",
                                              "status": 400,
                                              "error": "Bad Request",
                                              "message": "maxTransporters: maxTransporters must be at least 1",
                                              "path": "/api/v1/transporters/assignment"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No input data found in database",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Missing Data",
                                    value = """
                                            {
                                              "timestamp": "2026-11-13T12:00:00",
                                              "status": 404,
                                              "error": "Not Found",
                                              "message": "No input data found. Please submit input data first using POST /api/v1/transporters/input",
                                              "path": "/api/v1/transporters/assignment"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "No feasible assignment exists (unquoted lane or restrictive maxTransporters)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Infeasible Assignment",
                                    value = """
                                            {
                                              "timestamp": "2026-11-13T12:00:00",
                                              "status": 422,
                                              "error": "Unprocessable Entity",
                                              "message": "No feasible assignment exists. Some lanes may have no eligible transporter quotes, or the maxTransporters constraint is too restrictive.",
                                              "path": "/api/v1/transporters/assignment"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<AssignmentResponse> generateAssignment(
            @Valid @RequestBody AssignmentRequest request) {
        log.info("POST /api/v1/transporters/assignment — maxTransporters={}",
                request.getMaxTransporters());

        AssignmentResponse response = transporterService.generateAssignment(request.getMaxTransporters());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/current")
    @Operation(
            summary = "Get current active tender dataset",
            description = "Retrieves the active lanes, transporters, and quotations currently persisted in the database."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Active dataset successfully retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentDatasetResponse.class))
            )
    })
    public ResponseEntity<CurrentDatasetResponse> getCurrentDataset() {
        log.info("GET /api/v1/transporters/current");
        CurrentDatasetResponse response = transporterService.getCurrentDataset();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @Operation(
            summary = "Atomically unload and clear tender dataset",
            description = "Clears all lanes, transporters, and quotations from the database atomically."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Dataset successfully cleared",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteResponse.class))
            )
    })
    public ResponseEntity<DeleteResponse> clearDataset() {
        log.info("DELETE /api/v1/transporters");
        DeleteResponse response = transporterService.clearAllData();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @Operation(
            summary = "Get system runtime status and operator diagnostics",
            description = "Returns real-time service health, database state, algorithm specifications, and operator session details."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "System status retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SystemStatusResponse.class))
            )
    })
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        log.info("GET /api/v1/transporters/status");
        SystemStatusResponse response = transporterService.getSystemStatus();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/assignment/latest")
    @Operation(
            summary = "Get latest optimal assignment solution",
            description = "Retrieves the most recently computed optimal assignment result."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Latest assignment solution retrieved",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AssignmentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No assignment solution currently computed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AssignmentResponse> getLatestAssignment() {
        log.info("GET /api/v1/transporters/assignment/latest");
        return transporterService.getLatestAssignment()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
