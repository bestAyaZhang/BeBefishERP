package com.bebefish.erp.shipping.domain;

import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShipmentRepository {
    Shipment insert(Shipment shipment);
    boolean update(Shipment shipment, long expectedVersion);
    Optional<Shipment> findById(long id);
    Page<Shipment> findAll(ShipmentQuery query, Pageable pageable);
    ShipmentSummary summary(LocalDate date);
    boolean hasNonRejectedLogisticsOrder(long id);
}
