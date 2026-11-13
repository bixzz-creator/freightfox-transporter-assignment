package com.freightfox.transporter.repository;

import com.freightfox.transporter.entity.LaneQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LaneQuoteRepository extends JpaRepository<LaneQuote, Long> {

    /**
     * Fetches all lane quotes with their associated lane and transporter eagerly loaded.
     * Avoids N+1 query problems by using a single JOIN FETCH query.
     */
    @Query("SELECT lq FROM LaneQuote lq JOIN FETCH lq.lane JOIN FETCH lq.transporter ORDER BY lq.lane.id, lq.transporter.id")
    List<LaneQuote> findAllWithLaneAndTransporter();
}

