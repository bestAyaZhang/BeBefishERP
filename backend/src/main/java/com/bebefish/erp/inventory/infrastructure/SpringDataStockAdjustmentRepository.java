package com.bebefish.erp.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataStockAdjustmentRepository extends JpaRepository<StockAdjustmentJpaEntity, Long> {
}
