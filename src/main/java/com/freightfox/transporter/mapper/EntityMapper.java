package com.freightfox.transporter.mapper;

import com.freightfox.transporter.dto.request.LaneDto;
import com.freightfox.transporter.entity.Lane;
import com.freightfox.transporter.entity.Transporter;
import com.freightfox.transporter.dto.request.TransporterDto;

/**
 * Maps between DTOs and JPA entities.
 * Keeps mapping logic centralized and avoids leaking entities into the API layer.
 */
public final class EntityMapper {

    private EntityMapper() {
        // Utility class — no instantiation
    }

    public static Lane toLaneEntity(LaneDto dto) {
        return new Lane(dto.getId(), dto.getOrigin().trim(), dto.getDestination().trim());
    }

    public static Transporter toTransporterEntity(TransporterDto dto) {
        return new Transporter(dto.getId(), dto.getName().trim());
    }
}

