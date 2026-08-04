package com.bebefish.erp.sales.domain;

import java.math.BigDecimal;

public record SalesOrderItem(
        Long id,
        Long skuId,
        BigDecimal quantity,
        BigDecimal defaultUnitPrice,
        BigDecimal unitPrice,
        BigDecimal discountRate,
        BigDecimal amount,
        BigDecimal standardCostSnapshot,
        String itemNoSnapshot,
        String productNameSnapshot,
        String skuCodeSnapshot,
        String skuNameSnapshot,
        String specificationSnapshot,
        String packagingSnapshot,
        Integer cartonQuantitySnapshot,
        String barcodeSnapshot,
        String salesUnitSnapshot
) {
}
