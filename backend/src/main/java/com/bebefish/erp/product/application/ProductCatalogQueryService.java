package com.bebefish.erp.product.application;

import com.bebefish.erp.product.application.ProductCatalogMetrics.SkuCatalogMetrics;
import com.bebefish.erp.product.application.ProductCatalogMetrics.CatalogSupplierQuote;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.SupplierQuote;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogQueryService {
    private final ProductRepository repository;
    private final ProductCompletenessCalculator completenessCalculator;

    public ProductCatalogQueryService(
            ProductRepository repository,
            ProductCompletenessCalculator completenessCalculator
    ) {
        this.repository = repository;
        this.completenessCalculator = completenessCalculator;
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductCatalogMetrics> load(List<Product> products) {
        var productIds = products.stream()
                .map(Product::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }

        var catalogData = repository.loadCatalogData(productIds);
        var metricsByProductId = new LinkedHashMap<Long, ProductCatalogMetrics>();
        for (var product : products) {
            if (product.id() == null) {
                continue;
            }
            var quotesBySkuId = new LinkedHashMap<Long, List<SupplierQuote>>();
            var skuMetrics = new LinkedHashMap<Long, SkuCatalogMetrics>();
            var totalStock = BigDecimal.ZERO;
            var totalSafetyStock = BigDecimal.ZERO;

            for (var sku : product.skus()) {
                if (sku.id() == null) {
                    continue;
                }
                var stockQuantity = catalogData.stockQuantityBySkuId()
                        .getOrDefault(sku.id(), BigDecimal.ZERO);
                var supplierQuotes = catalogData.supplierQuotesBySkuId()
                        .getOrDefault(sku.id(), List.of());
                quotesBySkuId.put(sku.id(), supplierQuotes);
                var catalogQuotes = supplierQuotes.stream()
                        .map(quote -> new CatalogSupplierQuote(
                                quote, catalogData.supplierNameByQuoteId().get(quote.id())
                        ))
                        .toList();
                skuMetrics.put(sku.id(), new SkuCatalogMetrics(stockQuantity, catalogQuotes));
                totalStock = totalStock.add(stockQuantity);
                totalSafetyStock = totalSafetyStock.add(orZero(sku.safetyStockQuantity()));
            }

            var imageUrls = imageUrls(product, catalogData.imageUrlByFileId());
            metricsByProductId.put(product.id(), new ProductCatalogMetrics(
                    catalogData.categoryNameByProductId().get(product.id()),
                    totalStock,
                    totalSafetyStock,
                    catalogData.defaultSalePriceByProductId().get(product.id()),
                    catalogData.defaultSupplierNameByProductId().get(product.id()),
                    completenessCalculator.calculate(product, quotesBySkuId),
                    skuMetrics,
                    imageUrls
            ));
        }
        return Map.copyOf(metricsByProductId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> categoryCounts() {
        return repository.categoryCounts();
    }

    private Map<Long, String> imageUrls(Product product, Map<Long, String> allImageUrls) {
        var imageUrls = new LinkedHashMap<Long, String>();
        addImageUrl(imageUrls, allImageUrls, product.mainImageFileId());
        for (var sku : product.skus()) {
            addImageUrl(imageUrls, allImageUrls, sku.skuImageFileId());
            if (sku.packaging() != null) {
                addImageUrl(imageUrls, allImageUrls, sku.packaging().packageImageFileId());
                addImageUrl(imageUrls, allImageUrls, sku.packaging().cartonImageFileId());
            }
        }
        return imageUrls;
    }

    private void addImageUrl(Map<Long, String> imageUrls, Map<Long, String> allImageUrls, Long fileId) {
        if (fileId != null && allImageUrls.containsKey(fileId)) {
            imageUrls.put(fileId, allImageUrls.get(fileId));
        }
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
