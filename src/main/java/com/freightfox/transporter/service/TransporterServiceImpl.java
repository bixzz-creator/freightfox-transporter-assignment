package com.freightfox.transporter.service;

import com.freightfox.transporter.dto.request.InputRequest;
import com.freightfox.transporter.dto.request.LaneDto;
import com.freightfox.transporter.dto.request.LaneQuoteDto;
import com.freightfox.transporter.dto.request.TransporterDto;
import com.freightfox.transporter.dto.response.*;
import com.freightfox.transporter.entity.Lane;
import com.freightfox.transporter.entity.LaneQuote;
import com.freightfox.transporter.entity.Transporter;
import com.freightfox.transporter.exception.InputDataNotFoundException;
import com.freightfox.transporter.exception.TransporterLimitExceededException;
import com.freightfox.transporter.mapper.EntityMapper;
import com.freightfox.transporter.optimizer.AssignmentCandidate;
import com.freightfox.transporter.optimizer.AssignmentOptimizer;
import com.freightfox.transporter.optimizer.OptimizationResult;
import com.freightfox.transporter.repository.LaneQuoteRepository;
import com.freightfox.transporter.repository.LaneRepository;
import com.freightfox.transporter.repository.TransporterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the transporter assignment service.
 * Handles input validation, data persistence, and optimization orchestration.
 */
@Service
public class TransporterServiceImpl implements TransporterService {

    private static final Logger log = LoggerFactory.getLogger(TransporterServiceImpl.class);

    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final LaneQuoteRepository laneQuoteRepository;
    private final AssignmentOptimizer optimizer;
    private final int maxSupportedTransporters;

    public TransporterServiceImpl(LaneRepository laneRepository,
                                  TransporterRepository transporterRepository,
                                  LaneQuoteRepository laneQuoteRepository,
                                  AssignmentOptimizer optimizer,
                                  @Value("${app.optimizer.max-supported-transporters:20}") int maxSupportedTransporters) {
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.laneQuoteRepository = laneQuoteRepository;
        this.optimizer = optimizer;
        this.maxSupportedTransporters = maxSupportedTransporters;
    }

    @Override
    @Transactional
    public InputResponse saveInputData(InputRequest request) {
        // Step 1: Validate the entire request BEFORE any database modifications
        validateInputRequest(request);

        log.info("Saving input data: {} lanes, {} transporters",
                request.getLanes().size(), request.getTransporters().size());

        // Step 2: Atomic replacement — delete all existing data
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();

        // Step 3: Save lanes
        Map<Long, Lane> laneMap = new LinkedHashMap<>();
        for (LaneDto dto : request.getLanes()) {
            Lane lane = EntityMapper.toLaneEntity(dto);
            laneMap.put(lane.getId(), lane);
        }
        laneRepository.saveAll(laneMap.values());

        // Step 4: Save transporters and their quotes
        int totalQuotes = 0;
        List<Transporter> transportersToSave = new ArrayList<>();
        List<LaneQuote> allQuotes = new ArrayList<>();

        for (TransporterDto dto : request.getTransporters()) {
            Transporter transporter = EntityMapper.toTransporterEntity(dto);
            transportersToSave.add(transporter);

            for (LaneQuoteDto quoteDto : dto.getLaneQuotes()) {
                Lane lane = laneMap.get(quoteDto.getLaneId());
                LaneQuote laneQuote = new LaneQuote(lane, transporter, quoteDto.getQuote());
                allQuotes.add(laneQuote);
                totalQuotes++;
            }
        }
        transporterRepository.saveAll(transportersToSave);
        laneQuoteRepository.saveAll(allQuotes);

        log.info("Input data saved: {} lanes, {} transporters, {} quotes",
                laneMap.size(), request.getTransporters().size(), totalQuotes);

        return new InputResponse(
                "SUCCESS",
                laneMap.size(),
                request.getTransporters().size(),
                totalQuotes,
                "Input data saved successfully. Previous data has been replaced."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse generateAssignment(int maxTransporters) {
        if (maxTransporters <= 0) {
            throw new IllegalArgumentException("maxTransporters must be at least 1, received: " + maxTransporters);
        }

        // Step 1: Load all data from database with JOIN FETCH (avoids N+1)
        List<LaneQuote> quotes = laneQuoteRepository.findAllWithLaneAndTransporter();
        List<Lane> lanes = laneRepository.findAll();
        List<Transporter> transporters = transporterRepository.findAll();

        if (lanes.isEmpty() || transporters.isEmpty() || quotes.isEmpty()) {
            throw new InputDataNotFoundException(
                    "No input data found. Please submit input data first using POST /api/v1/transporters/input");
        }

        // Step 2: Validate transporter count against DP algorithm limit
        if (transporters.size() > maxSupportedTransporters) {
            throw new TransporterLimitExceededException(
                    "Number of transporters (" + transporters.size() + ") exceeds the maximum supported by " +
                    "the bitmask DP algorithm (" + maxSupportedTransporters + "). " +
                    "Reduce the number of transporters or use a different algorithm.");
        }

        // Step 3: Sort transporters by ID ascending for deterministic bit assignment
        List<Transporter> sortedTransporters = new ArrayList<>(transporters);
        sortedTransporters.sort(Comparator.comparingLong(Transporter::getId));

        List<Lane> sortedLanes = new ArrayList<>(lanes);
        sortedLanes.sort(Comparator.comparingLong(Lane::getId));

        List<Long> laneIds = sortedLanes.stream().map(Lane::getId).toList();
        List<Long> transporterIds = sortedTransporters.stream().map(Transporter::getId).toList();

        // Step 4: Build the quotes matrix — quotes[laneIdx][transporterIdx]
        // null means no quote (transporter cannot serve that lane)
        Map<Long, Integer> laneIndexMap = new HashMap<>();
        for (int i = 0; i < laneIds.size(); i++) {
            laneIndexMap.put(laneIds.get(i), i);
        }
        Map<Long, Integer> transporterIndexMap = new HashMap<>();
        for (int i = 0; i < transporterIds.size(); i++) {
            transporterIndexMap.put(transporterIds.get(i), i);
        }

        BigDecimal[][] quoteMatrix = new BigDecimal[sortedLanes.size()][sortedTransporters.size()];

        for (LaneQuote lq : quotes) {
            int laneIdx = laneIndexMap.get(lq.getLane().getId());
            int transporterIdx = transporterIndexMap.get(lq.getTransporter().getId());
            quoteMatrix[laneIdx][transporterIdx] = lq.getQuoteAmount();
        }

        // Step 5: Run the optimizer
        log.info("Running optimization: {} lanes x {} transporters, maxTransporters={}",
                sortedLanes.size(), sortedTransporters.size(), maxTransporters);

        OptimizationResult result = optimizer.optimize(laneIds, transporterIds, quoteMatrix, maxTransporters);

        // Step 6: Build the response
        Map<Long, Lane> laneMap = sortedLanes.stream().collect(Collectors.toMap(Lane::getId, l -> l));
        Map<Long, Transporter> transporterMap = sortedTransporters.stream()
                .collect(Collectors.toMap(Transporter::getId, t -> t));

        List<AssignmentDetailDto> assignmentDetails = new ArrayList<>();
        for (AssignmentCandidate ac : result.getAssignments()) {
            Lane lane = laneMap.get(ac.getLaneId());
            Transporter transporter = transporterMap.get(ac.getTransporterId());
            assignmentDetails.add(new AssignmentDetailDto(
                    lane.getId(), lane.getOrigin(), lane.getDestination(),
                    transporter.getId(), transporter.getName(), ac.getQuote()
            ));
        }

        List<SelectedTransporterDto> selectedTransporters = result.getUsedTransporterIds().stream()
                .sorted()
                .map(id -> {
                    Transporter t = transporterMap.get(id);
                    return new SelectedTransporterDto(t.getId(), t.getName());
                })
                .toList();

        AssignmentResponse response = new AssignmentResponse();
        response.setStatus("SUCCESS");
        response.setTotalCost(result.getTotalCost());
        response.setSelectedTransporters(selectedTransporters);
        response.setAssignments(assignmentDetails);
        response.setTotalLanes(lanes.size());
        response.setAssignedLanes(assignmentDetails.size());
        response.setTransporterCount(selectedTransporters.size());

        return response;
    }

    // ===================== Validation Methods =====================

    /**
     * Validates the entire input request before any database modifications.
     * Throws IllegalArgumentException with a descriptive message on the first violation found.
     */
    private void validateInputRequest(InputRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null");
        }
        if (request.getLanes() == null || request.getLanes().isEmpty()) {
            throw new IllegalArgumentException("Lanes list must not be empty");
        }
        if (request.getTransporters() == null || request.getTransporters().isEmpty()) {
            throw new IllegalArgumentException("Transporters list must not be empty");
        }

        validateLaneFields(request.getLanes());
        validateTransporterFields(request.getTransporters());
        validateNoDuplicateLaneIds(request.getLanes());
        validateNoDuplicateTransporterIds(request.getTransporters());
        validateLaneReferences(request);
        validateNoDuplicateQuotes(request.getTransporters());
        validateQuotesPositive(request.getTransporters());
    }

    private void validateLaneFields(List<LaneDto> lanes) {
        for (LaneDto lane : lanes) {
            if (lane.getId() == null) {
                throw new IllegalArgumentException("Lane ID must not be null");
            }
            if (lane.getOrigin() == null || lane.getOrigin().trim().isEmpty()) {
                throw new IllegalArgumentException("Lane origin must not be blank for lane ID " + lane.getId());
            }
            if (lane.getDestination() == null || lane.getDestination().trim().isEmpty()) {
                throw new IllegalArgumentException("Lane destination must not be blank for lane ID " + lane.getId());
            }
        }
    }

    private void validateTransporterFields(List<TransporterDto> transporters) {
        for (TransporterDto t : transporters) {
            if (t.getId() == null) {
                throw new IllegalArgumentException("Transporter ID must not be null");
            }
            if (t.getName() == null || t.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Transporter name must not be blank for transporter ID " + t.getId());
            }
        }
    }

    private void validateNoDuplicateLaneIds(List<LaneDto> lanes) {
        Set<Long> seen = new HashSet<>();
        for (LaneDto lane : lanes) {
            if (!seen.add(lane.getId())) {
                throw new IllegalArgumentException("Duplicate lane ID: " + lane.getId());
            }
        }
    }

    private void validateNoDuplicateTransporterIds(List<TransporterDto> transporters) {
        Set<Long> seen = new HashSet<>();
        for (TransporterDto t : transporters) {
            if (!seen.add(t.getId())) {
                throw new IllegalArgumentException("Duplicate transporter ID: " + t.getId());
            }
        }
    }

    private void validateLaneReferences(InputRequest request) {
        Set<Long> validLaneIds = request.getLanes().stream()
                .map(LaneDto::getId)
                .collect(Collectors.toSet());

        for (TransporterDto transporter : request.getTransporters()) {
            if (transporter.getLaneQuotes() == null || transporter.getLaneQuotes().isEmpty()) {
                throw new IllegalArgumentException(
                        "Transporter '" + transporter.getName() + "' (ID: " + transporter.getId() +
                        ") has no lane quotes");
            }
            for (LaneQuoteDto quote : transporter.getLaneQuotes()) {
                if (quote.getLaneId() == null) {
                    throw new IllegalArgumentException(
                            "Quote lane ID must not be null for transporter ID: " + transporter.getId());
                }
                if (!validLaneIds.contains(quote.getLaneId())) {
                    throw new IllegalArgumentException(
                            "Transporter '" + transporter.getName() + "' (ID: " + transporter.getId() +
                            ") references unknown lane ID: " + quote.getLaneId());
                }
            }
        }
    }

    private void validateNoDuplicateQuotes(List<TransporterDto> transporters) {
        for (TransporterDto transporter : transporters) {
            Set<Long> seenLaneIds = new HashSet<>();
            for (LaneQuoteDto quote : transporter.getLaneQuotes()) {
                if (!seenLaneIds.add(quote.getLaneId())) {
                    throw new IllegalArgumentException(
                            "Transporter '" + transporter.getName() + "' (ID: " + transporter.getId() +
                            ") has duplicate quote for lane ID: " + quote.getLaneId());
                }
            }
        }
    }

    private void validateQuotesPositive(List<TransporterDto> transporters) {
        for (TransporterDto transporter : transporters) {
            for (LaneQuoteDto quote : transporter.getLaneQuotes()) {
                if (quote.getQuote() == null || quote.getQuote().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "Quote amount for lane ID " + quote.getLaneId() +
                            " from transporter ID " + transporter.getId() + " must be strictly positive");
                }
            }
        }
    }
}
