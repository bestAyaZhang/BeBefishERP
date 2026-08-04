package com.bebefish.erp.inventory.infrastructure;

import com.bebefish.erp.inventory.domain.InventoryBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_balance")
class InventoryBalanceJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private long skuId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "version_no", nullable = false)
    private long versionNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected InventoryBalanceJpaEntity() {
    }

    InventoryBalanceJpaEntity(InventoryBalance balance) {
        apply(balance);
    }

    void apply(InventoryBalance balance) {
        warehouseId = balance.warehouseId();
        skuId = balance.skuId();
        quantity = balance.quantity();
        versionNo = balance.versionNo();
    }

    InventoryBalance toDomain() {
        return new InventoryBalance(id, warehouseId, skuId, quantity, versionNo);
    }

    @PrePersist
    void prePersist() {
        var now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
