package com.bebefish.erp.inventory.infrastructure;

import com.bebefish.erp.inventory.domain.StockAdjustment;
import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "stock_adjustment")
class StockAdjustmentJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_no", nullable = false, length = 50)
    private String adjustmentNo;

    @Column(name = "warehouse_id", nullable = false)
    private long warehouseId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private final List<StockAdjustmentItemJpaEntity> items = new ArrayList<>();

    protected StockAdjustmentJpaEntity() {
    }

    StockAdjustmentJpaEntity(StockAdjustment adjustment) {
        apply(adjustment);
    }

    void apply(StockAdjustment adjustment) {
        adjustmentNo = adjustment.adjustmentNo();
        warehouseId = adjustment.warehouseId();
        reason = adjustment.reason();
        status = adjustment.status();
        remark = adjustment.remark();
        Map<Long, StockAdjustmentItemJpaEntity> existingBySku = new HashMap<>();
        items.forEach(item -> existingBySku.put(item.skuId(), item));
        var incomingSkuIds = adjustment.items().stream()
                .map(StockAdjustmentItem::skuId)
                .toList();
        items.removeIf(item -> !incomingSkuIds.contains(item.skuId()));
        adjustment.items().forEach(item -> {
            var existing = existingBySku.get(item.skuId());
            if (existing == null) {
                items.add(new StockAdjustmentItemJpaEntity(this, item));
            } else {
                existing.apply(item);
            }
        });
    }

    StockAdjustment toDomain() {
        return new StockAdjustment(
                id, adjustmentNo, warehouseId, reason, status, remark,
                items.stream().map(StockAdjustmentItemJpaEntity::toDomain).toList(),
                createdAt, updatedAt
        );
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
