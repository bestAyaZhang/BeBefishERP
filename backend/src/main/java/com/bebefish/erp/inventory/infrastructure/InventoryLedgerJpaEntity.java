package com.bebefish.erp.inventory.infrastructure;

import com.bebefish.erp.inventory.domain.InventoryLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_ledger")
class InventoryLedgerJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private long skuId;

    @Column(nullable = false, length = 20)
    private String direction;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "before_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal beforeQuantity;

    @Column(name = "after_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal afterQuantity;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @Column(name = "source_no", nullable = false, length = 50)
    private String sourceNo;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "operator_mobile", nullable = false, length = 30)
    private String operatorMobile;

    protected InventoryLedgerJpaEntity() {
    }

    InventoryLedgerJpaEntity(InventoryLedgerEntry entry) {
        warehouseId = entry.warehouseId();
        skuId = entry.skuId();
        direction = entry.direction();
        quantity = entry.quantity();
        beforeQuantity = entry.beforeQuantity();
        afterQuantity = entry.afterQuantity();
        sourceType = entry.sourceType();
        sourceId = entry.sourceId();
        sourceNo = entry.sourceNo();
        occurredAt = entry.occurredAt();
        operatorMobile = entry.operatorMobile();
    }

    InventoryLedgerEntry toDomain() {
        return new InventoryLedgerEntry(
                id, warehouseId, skuId, direction, quantity, beforeQuantity,
                afterQuantity, sourceType, sourceId, sourceNo, occurredAt, operatorMobile
        );
    }
}
