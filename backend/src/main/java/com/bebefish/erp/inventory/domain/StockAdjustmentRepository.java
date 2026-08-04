package com.bebefish.erp.inventory.domain;

import java.util.Optional;

public interface StockAdjustmentRepository {
    StockAdjustment save(StockAdjustment adjustment);

    Optional<StockAdjustment> findById(long id);

    void deleteById(long id);
}
