package com.bebefish.erp.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInventoryLedgerRepository extends JpaRepository<InventoryLedgerJpaEntity, Long> {
}
