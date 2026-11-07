package com.bebefish.erp.product.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        LocalDateTime updatedAt,
        List<ProductImagePack> imagePacks,
        List<Long> materialImages
) {
    public Product {
        specifications = List.copyOf(specifications);
        skus = List.copyOf(skus);
        imagePacks = imagePacks == null ? null : List.copyOf(imagePacks);
        materialImages = materialImages == null ? null : List.copyOf(materialImages);
    }

    public Product(
            Long id, String code, String itemNo, String name, Long categoryId, String brand, ProductType type,
            Long mainImageFileId, String status, String remark, List<Specification> specifications,
            List<Sku> skus, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        this(id, code, itemNo, name, categoryId, brand, type, mainImageFileId, status, remark, specifications,
                skus, createdAt, updatedAt, null, null);
    }

    public Stream<Long> imageFileIds() {
        return Stream.of(
                Stream.ofNullable(mainImageFileId),
                skus.stream().flatMap(Sku::imageFileIds),
                Stream.ofNullable(imagePacks).flatMap(List::stream).flatMap(ProductImagePack::fileIds),
                Stream.ofNullable(materialImages).flatMap(List::stream)
        ).flatMap(stream -> stream).filter(Objects::nonNull);
    }

    public Product withIdentity(Long productId, List<Sku> savedSkus) {
        return new Product(
                productId, code, itemNo, name, categoryId, brand, type,
                mainImageFileId, status, remark, specifications, savedSkus, createdAt, updatedAt,
                imagePacks, materialImages
        );
    }

    public Product withStatus(String nextStatus) {
        return new Product(
                id, code, itemNo, name, categoryId, brand, type,
                mainImageFileId, nextStatus, remark, specifications, skus, createdAt, updatedAt,
                imagePacks, materialImages
        );
    }
}
