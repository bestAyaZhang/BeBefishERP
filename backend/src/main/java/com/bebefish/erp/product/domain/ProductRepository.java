package com.bebefish.erp.product.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository {
    boolean existsByProductCode(String code, Long excludedProductId);

    boolean existsByItemNo(String itemNo, Long excludedProductId);

    boolean existsBySkuCode(String code, Long excludedProductId);

    boolean existsByBarcode(String barcode, Long excludedProductId);

    Product save(Product product);

    String nextProductCode();

    Optional<Product> findById(long id);

    default Optional<Product> findByIdForUpdate(long id) {
        return findById(id);
    }

    Optional<Sku> findSku(long id);

    default Optional<Product> findProductBySkuId(long skuId) {
        return Optional.empty();
    }

    void updateSkuStandardCost(long id, BigDecimal standardCost);

    Page<Product> findAll(
            String keyword,
            Long categoryId,
            Long supplierId,
            String status,
            Pageable pageable
    );

    default Optional<String> findMainImageUrl(long productId) {
        return Optional.empty();
    }

    default Map<Long, String> findImageUrls(long productId) {
        return Map.of();
    }

    default Optional<String> findDefaultSupplierName(long productId) {
        return Optional.empty();
    }

    default Set<Long> findEnabledImageFileIds(Collection<Long> fileIds) {
        return Set.of();
    }

    default ProductCatalogData loadCatalogData(Collection<Long> productIds, Collection<Long> imageFileIds) {
        return ProductCatalogData.empty();
    }

    default Map<Long, Long> categoryCounts() {
        return Map.of();
    }

    record ProductCatalogData(
            Map<Long, BigDecimal> stockQuantityBySkuId,
            Map<Long, List<SupplierQuote>> supplierQuotesBySkuId,
            Map<Long, String> supplierNameByQuoteId,
            Map<Long, BigDecimal> defaultSalePriceByProductId,
            Map<Long, String> defaultSupplierNameByProductId,
            Map<Long, String> categoryNameByProductId,
            Map<Long, ImageFile> imageByFileId
    ) {
        public ProductCatalogData {
            stockQuantityBySkuId = Map.copyOf(stockQuantityBySkuId);
            var copiedQuotes = new LinkedHashMap<Long, List<SupplierQuote>>();
            supplierQuotesBySkuId.forEach((skuId, quotes) -> copiedQuotes.put(skuId, List.copyOf(quotes)));
            supplierQuotesBySkuId = Map.copyOf(copiedQuotes);
            supplierNameByQuoteId = Map.copyOf(supplierNameByQuoteId);
            defaultSalePriceByProductId = Map.copyOf(defaultSalePriceByProductId);
            defaultSupplierNameByProductId = Map.copyOf(defaultSupplierNameByProductId);
            categoryNameByProductId = Map.copyOf(categoryNameByProductId);
            imageByFileId = Map.copyOf(imageByFileId);
        }

        public static ProductCatalogData empty() {
            return new ProductCatalogData(
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
            );
        }
    }

    record ImageFile(String url, String name) {
    }
}
