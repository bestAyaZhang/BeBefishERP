package com.bebefish.erp.product.domain;

import java.util.Optional;
import java.math.BigDecimal;
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

    default Optional<String> findDefaultSupplierName(long productId) {
        return Optional.empty();
    }
}
