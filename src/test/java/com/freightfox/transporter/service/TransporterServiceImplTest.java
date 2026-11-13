package com.freightfox.transporter.service;

import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.request.LaneDto;
import com.freightfox.transporter.dto.request.LaneQuoteDto;
import com.freightfox.transporter.dto.request.TransporterDto;
import com.freightfox.transporter.dto.response.AssignmentResponse;
import com.freightfox.transporter.dto.response.InputResponse;
import com.freightfox.transporter.entity.Lane;
import com.freightfox.transporter.entity.LaneQuote;
import com.freightfox.transporter.entity.Transporter;
import com.freightfox.transporter.exception.InputDataNotFoundException;
import com.freightfox.transporter.exception.TransporterLimitExceededException;
import com.freightfox.transporter.optimizer.AssignmentCandidate;
import com.freightfox.transporter.optimizer.AssignmentOptimizer;
import com.freightfox.transporter.optimizer.OptimizationResult;
import com.freightfox.transporter.repository.LaneQuoteRepository;
import com.freightfox.transporter.repository.LaneRepository;
import com.freightfox.transporter.repository.TransporterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transporter Service Unit Tests")
class TransporterServiceImplTest {

    @Mock
    private LaneRepository laneRepository;

    @Mock
    private TransporterRepository transporterRepository;

    @Mock
    private LaneQuoteRepository laneQuoteRepository;

    @Mock
    private AssignmentOptimizer optimizer;

    private TransporterServiceImpl transporterService;

    @BeforeEach
    void setUp() {
        transporterService = new TransporterServiceImpl(
                laneRepository, transporterRepository, laneQuoteRepository, optimizer, 20);
    }

    @Test
    @DisplayName("saveInputData validates and saves successfully")
    void testSaveInputDataSuccess() {
        InputRequest request = new InputRequest(
                List.of(new LaneDto(1L, "City-A", "City-B")),
                List.of(new TransporterDto(10L, "Transporter-1", List.of(
                        new LaneQuoteDto(1L, new BigDecimal("100.00"))
                )))
        );

        when(laneRepository.saveAll(any())).thenReturn(Collections.emptyList());
        when(transporterRepository.saveAll(any())).thenReturn(Collections.emptyList());
        when(laneQuoteRepository.saveAll(any())).thenReturn(Collections.emptyList());

        InputResponse response = transporterService.saveInputData(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getLanesCount());
        assertEquals(1, response.getTransportersCount());
        assertEquals(1, response.getQuotesCount());

        verify(laneQuoteRepository).deleteAllInBatch();
        verify(transporterRepository).deleteAllInBatch();
        verify(laneRepository).deleteAllInBatch();
        verify(laneRepository).saveAll(any());
        verify(transporterRepository).saveAll(any());
        verify(laneQuoteRepository).saveAll(any());
    }

    @Test
    @DisplayName("generateAssignment throws InputDataNotFoundException when DB is empty")
    void testGenerateAssignmentThrowsWhenNoData() {
        when(laneQuoteRepository.findAllWithLaneAndTransporter()).thenReturn(Collections.emptyList());
        when(laneRepository.findAll()).thenReturn(Collections.emptyList());
        when(transporterRepository.findAll()).thenReturn(Collections.emptyList());

        assertThrows(InputDataNotFoundException.class, () ->
                transporterService.generateAssignment(2));
    }

    @Test
    @DisplayName("generateAssignment throws TransporterLimitExceededException when T > maxSupportedTransporters")
    void testGenerateAssignmentThrowsWhenLimitExceeded() {
        TransporterServiceImpl limitedService = new TransporterServiceImpl(
                laneRepository, transporterRepository, laneQuoteRepository, optimizer, 2);

        Lane lane = new Lane(1L, "A", "B");
        Transporter t1 = new Transporter(1L, "T1");
        Transporter t2 = new Transporter(2L, "T2");
        Transporter t3 = new Transporter(3L, "T3");

        when(laneRepository.findAll()).thenReturn(List.of(lane));
        when(transporterRepository.findAll()).thenReturn(List.of(t1, t2, t3)); // 3 > 2
        when(laneQuoteRepository.findAllWithLaneAndTransporter()).thenReturn(List.of(
                new LaneQuote(lane, t1, BigDecimal.TEN)
        ));

        assertThrows(TransporterLimitExceededException.class, () ->
                limitedService.generateAssignment(2));
    }

    @Test
    @DisplayName("generateAssignment maps optimizer results to AssignmentResponse correctly")
    void testGenerateAssignmentSuccess() {
        Lane lane = new Lane(1L, "City-A", "City-B");
        Transporter transporter = new Transporter(10L, "Apex Freight");
        LaneQuote quote = new LaneQuote(lane, transporter, new BigDecimal("1500.00"));

        when(laneRepository.findAll()).thenReturn(List.of(lane));
        when(transporterRepository.findAll()).thenReturn(List.of(transporter));
        when(laneQuoteRepository.findAllWithLaneAndTransporter()).thenReturn(List.of(quote));

        OptimizationResult optResult = new OptimizationResult(
                List.of(new AssignmentCandidate(0, 0, 1L, 10L, new BigDecimal("1500.00"))),
                new BigDecimal("1500.00"),
                Set.of(10L)
        );

        when(optimizer.optimize(anyList(), anyList(), any(), eq(1))).thenReturn(optResult);

        AssignmentResponse response = transporterService.generateAssignment(1);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(0, new BigDecimal("1500.00").compareTo(response.getTotalCost()));
        assertEquals(1, response.getTransporterCount());
        assertEquals(1, response.getTotalLanes());
        assertEquals(1, response.getAssignedLanes());
        assertEquals(1, response.getSelectedTransporters().size());
        assertEquals(10L, response.getSelectedTransporters().get(0).getTransporterId());
        assertEquals("Apex Freight", response.getSelectedTransporters().get(0).getTransporterName());
    }
}

