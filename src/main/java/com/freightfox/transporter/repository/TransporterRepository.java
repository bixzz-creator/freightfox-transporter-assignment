package com.freightfox.transporter.repository;

import com.freightfox.transporter.entity.Transporter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransporterRepository extends JpaRepository<Transporter, Long> {
}

