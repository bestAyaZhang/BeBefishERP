package com.bebefish.erp.shipping.domain;

import java.time.LocalDateTime;

public record Shipment(Long id, String shipmentNo, ShipmentContent content, long version,
                       String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {}
