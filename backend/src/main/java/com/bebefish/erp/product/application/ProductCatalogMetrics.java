package com.bebefish.erp.product.application;

import com.bebefish.erp.product.application.ProductCompletenessCalculator.ProductCompleteness;
import com.bebefish.erp.product.domain.SupplierQuote;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductCatalogMetrics(
        String categoryName,
        BigDecimal totalStock,
        BigDecimal totalSafetyStock,
        BigDecimal defaultSalePrice,
        String defaultSupplierName,
        ProductCompleteness completeness,
        Map<Long, SkuCatalogMetrics> skus,
        Map<Long, String> imageUrls
) {
    public ProductCatalogMetrics {
        skus = Map.copyOf(skus);
        imageUrls = Map.copyOf(imageUrls);
    }

    public record SkuCatalogMetrics(
            BigDecimal stockQuantity,
            List<CatalogSupplierQuote> supplierQuotes
    ) {
        public SkuCatalogMetrics {
            supplierQuotes = List.copyOf(supplierQuotes);
        }
    }

    public record CatalogSupplierQuote(SupplierQuote quote, String supplierName) {
    }
}
