package com.freightfox.transporter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.request.LaneDto;
import com.freightfox.transporter.dto.request.LaneQuoteDto;
import com.freightfox.transporter.dto.request.TransporterDto;
import com.freightfox.transporter.entity.Lane;
import com.freightfox.transporter.entity.LaneQuote;
import com.freightfox.transporter.entity.Transporter;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Part C: Data Layer and Input API Integration Tests")
class TransporterInputIntegrationTest {

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

    private InputRequest createSampleInputRequest() {
        List<LaneDto> lanes = List.of(
                new LaneDto(1L, "Mumbai", "Delhi"),
                new LaneDto(2L, "Delhi", "Bangalore")
        );

        List<TransporterDto> transporters = List.of(
                new TransporterDto(101L, "ABC Logistics", List.of(
                        new LaneQuoteDto(1L, new BigDecimal("5000.00")),
                        new LaneQuoteDto(2L, new BigDecimal("6000.00"))
                )),
                new TransporterDto(102L, "XYZ Express", List.of(
                        new LaneQuoteDto(1L, new BigDecimal("5500.00")),
                        new LaneQuoteDto(2L, new BigDecimal("5800.00"))
                ))
        );

        return new InputRequest(lanes, transporters);
    }

    @Test
    @DisplayName("1 & 2. Successful input submission and database persistence with foreign keys")
    void testSuccessfulInputSubmissionAndPersistence() throws Exception {
        InputRequest request = createSampleInputRequest();

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.lanesCount", is(2)))
                .andExpect(jsonPath("$.transportersCount", is(2)))
                .andExpect(jsonPath("$.quotesCount", is(4)));

        // Verify database persistence
        assertEquals(2, laneRepository.count());
        assertEquals(2, transporterRepository.count());
        assertEquals(4, laneQuoteRepository.count());

        // Verify foreign key integrity & relationships
        List<LaneQuote> quotes = laneQuoteRepository.findAllWithLaneAndTransporter();
        assertEquals(4, quotes.size());
        for (LaneQuote quote : quotes) {
            assertNotNull(quote.getLane(), "FK to Lane must not be null");
            assertNotNull(quote.getTransporter(), "FK to Transporter must not be null");
            assertNotNull(quote.getQuoteAmount(), "Quote amount must not be null");
            assertNotNull(quote.getCreatedAt(), "CreatedAt timestamp must be set");
            assertNotNull(quote.getUpdatedAt(), "UpdatedAt timestamp must be set");
        }
    }

    @Test
    @DisplayName("3. Full replacement removes old data and inserts new dataset")
    void testFullReplacementRemovesOldData() throws Exception {
        // Initial insert
        InputRequest firstRequest = createSampleInputRequest();
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        assertEquals(2, laneRepository.count());
        assertEquals(2, transporterRepository.count());
        assertEquals(4, laneQuoteRepository.count());

        // Replace with completely different dataset: 1 lane, 1 transporter, 1 quote
        InputRequest replacementRequest = new InputRequest(
                List.of(new LaneDto(99L, "Kolkata", "Chennai")),
                List.of(new TransporterDto(888L, "NewTrans Ltd", List.of(
                        new LaneQuoteDto(99L, new BigDecimal("1234.50"))
                )))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replacementRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lanesCount", is(1)))
                .andExpect(jsonPath("$.transportersCount", is(1)))
                .andExpect(jsonPath("$.quotesCount", is(1)));

        // Verify old data is completely gone
        assertEquals(1, laneRepository.count());
        assertEquals(1, transporterRepository.count());
        assertEquals(1, laneQuoteRepository.count());
        assertFalse(laneRepository.existsById(1L), "Old lane 1 should not exist");
        assertFalse(laneRepository.existsById(2L), "Old lane 2 should not exist");
        assertFalse(transporterRepository.existsById(101L), "Old transporter 101 should not exist");
        assertTrue(laneRepository.existsById(99L), "New lane 99 must exist");
        assertTrue(transporterRepository.existsById(888L), "New transporter 888 must exist");
    }

    @Test
    @DisplayName("4 & 11. Empty input body or empty lists return HTTP 400")
    void testEmptyInputRejected() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));

        // Empty lanes list
        InputRequest emptyLanes = new InputRequest(
                List.of(),
                List.of(new TransporterDto(101L, "T1", List.of(new LaneQuoteDto(1L, new BigDecimal("100")))))
        );
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyLanes)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("5. Duplicate lane IDs are rejected with HTTP 400")
    void testDuplicateLaneIdsRejected() throws Exception {
        InputRequest request = new InputRequest(
                List.of(
                        new LaneDto(1L, "Mumbai", "Delhi"),
                        new LaneDto(1L, "Delhi", "Bangalore") // Duplicate ID 1
                ),
                List.of(new TransporterDto(101L, "T1", List.of(new LaneQuoteDto(1L, new BigDecimal("100")))))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Duplicate lane ID: 1")));
    }

    @Test
    @DisplayName("6. Duplicate transporter IDs are rejected with HTTP 400")
    void testDuplicateTransporterIdsRejected() throws Exception {
        InputRequest request = new InputRequest(
                List.of(new LaneDto(1L, "Mumbai", "Delhi")),
                List.of(
                        new TransporterDto(101L, "T1", List.of(new LaneQuoteDto(1L, new BigDecimal("100")))),
                        new TransporterDto(101L, "T2", List.of(new LaneQuoteDto(1L, new BigDecimal("200")))) // Duplicate ID 101
                )
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Duplicate transporter ID: 101")));
    }

    @Test
    @DisplayName("7. Duplicate quotes for same lane by same transporter are rejected with HTTP 400")
    void testDuplicateQuotesRejected() throws Exception {
        InputRequest request = new InputRequest(
                List.of(new LaneDto(1L, "Mumbai", "Delhi")),
                List.of(new TransporterDto(101L, "T1", List.of(
                        new LaneQuoteDto(1L, new BigDecimal("100")),
                        new LaneQuoteDto(1L, new BigDecimal("200")) // Duplicate quote for lane 1
                )))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("duplicate quote for lane ID: 1")));
    }

    @Test
    @DisplayName("8. Quotes referencing unknown lanes are rejected with HTTP 400")
    void testQuoteReferencingUnknownLaneRejected() throws Exception {
        InputRequest request = new InputRequest(
                List.of(new LaneDto(1L, "Mumbai", "Delhi")),
                List.of(new TransporterDto(101L, "T1", List.of(
                        new LaneQuoteDto(999L, new BigDecimal("100")) // Lane 999 does not exist
                )))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("unknown lane ID: 999")));
    }

    @Test
    @DisplayName("9. Zero or negative quotes are rejected with HTTP 400")
    void testZeroOrNegativeQuoteRejected() throws Exception {
        // Zero quote
        InputRequest zeroQuoteRequest = new InputRequest(
                List.of(new LaneDto(1L, "Mumbai", "Delhi")),
                List.of(new TransporterDto(101L, "T1", List.of(
                        new LaneQuoteDto(1L, BigDecimal.ZERO)
                )))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroQuoteRequest)))
                .andExpect(status().isBadRequest());

        // Negative quote
        InputRequest negativeQuoteRequest = new InputRequest(
                List.of(new LaneDto(1L, "Mumbai", "Delhi")),
                List.of(new TransporterDto(101L, "T1", List.of(
                        new LaneQuoteDto(1L, new BigDecimal("-50.00"))
                )))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeQuoteRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("10. Transaction rollback preserves old dataset if new input fails validation")
    void testRollbackPreservesOldDataOnValidationFailure() throws Exception {
        // First, successfully save valid data
        InputRequest validRequest = createSampleInputRequest();
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

        assertEquals(2, laneRepository.count());
        assertEquals(2, transporterRepository.count());
        assertEquals(4, laneQuoteRepository.count());

        // Now submit an invalid request (has an unknown lane reference)
        InputRequest invalidRequest = new InputRequest(
                List.of(new LaneDto(10L, "CityA", "CityB")),
                List.of(new TransporterDto(201L, "BadTrans", List.of(
                        new LaneQuoteDto(999L, new BigDecimal("100.00")) // invalid lane
                )))
        );

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        // Verify the original data is completely intact (not deleted)
        assertEquals(2, laneRepository.count(), "Old lanes must be preserved");
        assertEquals(2, transporterRepository.count(), "Old transporters must be preserved");
        assertEquals(4, laneQuoteRepository.count(), "Old quotes must be preserved");
        assertTrue(laneRepository.existsById(1L));
        assertTrue(transporterRepository.existsById(101L));
    }
}

