package com.freightfox.transporter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.transporter.dto.request.AssignmentRequest;
import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.request.LaneDto;
import com.freightfox.transporter.dto.request.LaneQuoteDto;
import com.freightfox.transporter.dto.request.TransporterDto;
import com.freightfox.transporter.repository.LaneQuoteRepository;
import com.freightfox.transporter.repository.LaneRepository;
import com.freightfox.transporter.repository.TransporterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Assignment API Integration Tests")
class TransporterAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private TransporterRepository transporterRepository;

    @Autowired
    private LaneQuoteRepository laneQuoteRepository;

    @BeforeEach
    void setUp() {
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();
    }

    private void seedNetworkData() throws Exception {
        InputRequest request = new InputRequest(
                List.of(
                        new LaneDto(1L, "Hub-Alpha", "Hub-Beta"),
                        new LaneDto(2L, "Hub-Beta", "Hub-Gamma"),
                        new LaneDto(3L, "Hub-Gamma", "Hub-Delta")
                ),
                List.of(
                        new TransporterDto(10L, "Express Logistics", List.of(
                                new LaneQuoteDto(1L, new BigDecimal("1200.00")),
                                new LaneQuoteDto(2L, new BigDecimal("2400.00")),
                                new LaneQuoteDto(3L, new BigDecimal("3100.00"))
                        )),
                        new TransporterDto(20L, "Rapid Freight", List.of(
                                new LaneQuoteDto(1L, new BigDecimal("1100.00")),
                                new LaneQuoteDto(2L, new BigDecimal("2600.00")),
                                new LaneQuoteDto(3L, new BigDecimal("2900.00"))
                        )),
                        new TransporterDto(30L, "Apex Movers", List.of(
                                new LaneQuoteDto(1L, new BigDecimal("1300.00")),
                                new LaneQuoteDto(2L, new BigDecimal("2100.00")),
                                new LaneQuoteDto(3L, new BigDecimal("3500.00"))
                        ))
                )
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Returns 404 when generating assignment without any input data")
    void testAssignmentReturns404WhenNoData() throws Exception {
        AssignmentRequest request = new AssignmentRequest(2);

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("No input data found")));
    }

    @Test
    @DisplayName("Returns 400 when maxTransporters is invalid (zero or negative)")
    void testAssignmentReturns400OnInvalidMaxTransporters() throws Exception {
        AssignmentRequest requestZero = new AssignmentRequest(0);

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestZero)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Generates valid optimized assignment honoring maxTransporters = 2")
    void testGenerateValidAssignment() throws Exception {
        seedNetworkData();

        AssignmentRequest request = new AssignmentRequest(2);

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.totalLanes", is(3)))
                .andExpect(jsonPath("$.assignedLanes", is(3)))
                .andExpect(jsonPath("$.transporterCount", is(2)))
                .andExpect(jsonPath("$.selectedTransporters", hasSize(2)))
                .andExpect(jsonPath("$.assignments", hasSize(3)))
                .andExpect(jsonPath("$.totalCost", notNullValue()));
    }

    @Test
    @DisplayName("Returns 422 when assignment is infeasible due to missing lane coverage")
    void testInfeasibleAssignmentReturns422() throws Exception {
        InputRequest request = new InputRequest(
                List.of(
                        new LaneDto(1L, "Point-A", "Point-B"),
                        new LaneDto(2L, "Point-C", "Point-D")
                ),
                List.of(
                        new TransporterDto(10L, "Solo Carrier", List.of(
                                new LaneQuoteDto(1L, new BigDecimal("1000.00"))
                                // Lane 2 has no quote from anyone
                        ))
                )
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        AssignmentRequest assignRequest = new AssignmentRequest(2);

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.error", is("Unprocessable Entity")))
                .andExpect(jsonPath("$.message", containsString("No feasible assignment exists")));
    }
}

