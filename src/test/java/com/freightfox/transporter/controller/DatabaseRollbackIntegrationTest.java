package com.freightfox.transporter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Transaction Rollback on Persistence Failure Test")
class DatabaseRollbackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private TransporterRepository transporterRepository;

    @SpyBean
    private LaneQuoteRepository laneQuoteRepository;

    @BeforeEach
    void setUp() {
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Simulate DB persistence failure after deletion and initial insertion: original dataset restored")
    void testRollbackRestoresOriginalDataAfterMidPersistenceFailure() throws Exception {
        // Step 1: Seed the initial dataset
        InputRequest initialRequest = new InputRequest(
                List.of(
                        new LaneDto(1L, "Original-Origin", "Original-Dest")
                ),
                List.of(
                        new TransporterDto(100L, "Original Transporter", List.of(
                                new LaneQuoteDto(1L, new BigDecimal("1000.00"))
                        ))
                )
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        assertEquals(1, laneRepository.count(), "Initial lane must be present");
        assertEquals(1, transporterRepository.count(), "Initial transporter must be present");
        assertEquals(1, laneQuoteRepository.count(), "Initial quote must be present");

        // Step 2: Configure SpyBean to simulate a failure on laneQuoteRepository.saveAll()
        // At this point during saveInputData(), deleteAllInBatch() and laneRepository.saveAll()
        // and transporterRepository.saveAll() have already executed within the active transaction!
        doThrow(new DataIntegrityViolationException("Simulated database failure during quote persistence"))
                .when(laneQuoteRepository).saveAll(any());

        // Step 3: Attempt a full replacement with a new dataset
        InputRequest replacementRequest = new InputRequest(
                List.of(
                        new LaneDto(2L, "New-Origin", "New-Dest")
                ),
                List.of(
                        new TransporterDto(200L, "New Transporter", List.of(
                                new LaneQuoteDto(2L, new BigDecimal("2000.00"))
                        ))
                )
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replacementRequest)))
                .andExpect(status().isInternalServerError());

        // Step 4: Verify the transaction rolled back completely:
        // Original data must still exist in the database!
        assertEquals(1, laneRepository.count(), "Original lane count must remain 1 after rollback");
        assertEquals(1, transporterRepository.count(), "Original transporter count must remain 1 after rollback");
        assertEquals(1, laneQuoteRepository.count(), "Original quote count must remain 1 after rollback");

        assertTrue(laneRepository.existsById(1L), "Original lane ID 1 must be preserved");
        assertTrue(transporterRepository.existsById(100L), "Original transporter ID 100 must be preserved");
    }
}

