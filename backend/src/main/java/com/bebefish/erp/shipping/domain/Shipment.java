package com.bebefish.erp.shipping.domain;

import java.time.LocalDateTime;
import java.util.List;

public record Shipment(Long id, String shipmentNo, ShipmentContent content, long version,
                       String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                       String logisticsOrderState, List<Long> preparerEmployeeIds, PreparationProgress preparation) {
    public Shipment {
        preparerEmployeeIds = preparerEmployeeIds == null ? List.of() : List.copyOf(preparerEmployeeIds);
        preparation = preparation == null ? PreparationProgress.empty() : preparation;
    }
    public Shipment(Long id, String shipmentNo, ShipmentContent content, long version,
                    String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                    String logisticsOrderState) {
        this(id, shipmentNo, content, version, createdBy, updatedBy, createdAt, updatedAt, logisticsOrderState, List.of(), null);
    }
    public Shipment(Long id, String shipmentNo, ShipmentContent content, long version,
                    String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, shipmentNo, content, version, createdBy, updatedBy, createdAt, updatedAt, null);
    }
}
