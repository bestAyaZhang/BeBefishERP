package com.bebefish.erp.inventory.infrastructure;

import com.bebefish.erp.inventory.domain.StockAdjustmentItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_adjustment_item")
class StockAdjustmentItemJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private StockAdjustmentJpaEntity adjustment;

    @Column(name = "sku_id", nullable = false)
    private long skuId;

    @Column(name = "quantity_delta", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityDelta;

    protected StockAdjustmentItemJpaEntity() {
    }

    StockAdjustmentItemJpaEntity(StockAdjustmentJpaEntity adjustment, StockAdjustmentItem item) {
        this.adjustment = adjustment;
        apply(item);
    }

    void apply(StockAdjustmentItem item) {
        this.skuId = item.skuId();
        this.quantityDelta = item.quantityDelta();
    }

    long skuId() {
        return skuId;
    }

    StockAdjustmentItem toDomain() {
        return new StockAdjustmentItem(id, skuId, quantityDelta);
    }
}
