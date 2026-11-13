package com.freightfox.transporter.repository;

import com.freightfox.transporter.entity.Lane;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LaneRepository extends JpaRepository<Lane, Long> {
}

