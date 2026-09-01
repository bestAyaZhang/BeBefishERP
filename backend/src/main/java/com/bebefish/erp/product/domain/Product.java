package com.bebefish.erp.product.domain;

import java.time.LocalDateTime;
import java.util.List;

public record Product(
        Long id,
        String code,
        String itemNo,
        String name,
        Long categoryId,
        String brand,
        ProductType type,
        Long mainImageFileId,
        String status,
        String remark,
        List<Specification> specifications,
        List<Sku> skus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public Product {
        specifications = List.copyOf(specifications);
        skus = List.copyOf(skus);
    }

    public Product withIdentity(Long productId, List<Sku> savedSkus) {
        return new Product(
                productId, code, itemNo, name, categoryId, brand, type,
                mainImageFileId, status, remark, specifications, savedSkus, createdAt, updatedAt
        );
    }

    public Product withStatus(String nextStatus) {
        return new Product(
                id, code, itemNo, name, categoryId, brand, type,
                mainImageFileId, nextStatus, remark, specifications, skus, createdAt, updatedAt
        );
    }
}
