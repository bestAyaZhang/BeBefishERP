package com.bebefish.erp.product.application;

import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.ProductImagePack;
import com.bebefish.erp.product.domain.Specification;
import java.util.List;

public record SaveProductCommand(
        String productCode,
        String itemNo,
        String productName,
        Long categoryId,
        String brand,
        ProductType type,
        Long mainImageFileId,
        String status,
        String remark,
        List<Specification> specifications,
        List<SaveSkuCommand> skus,
        List<ProductImagePack> imagePacks,
        List<Long> materialImages
) {
    public SaveProductCommand {
        specifications = specifications == null ? List.of() : List.copyOf(specifications);
        skus = skus == null ? List.of() : List.copyOf(skus);
        imagePacks = imagePacks == null ? null : List.copyOf(imagePacks);
        materialImages = materialImages == null ? null : List.copyOf(materialImages);
    }

    public SaveProductCommand(
            String productCode, String itemNo, String productName, Long categoryId, String brand, ProductType type,
            Long mainImageFileId, String status, String remark, List<Specification> specifications,
            List<SaveSkuCommand> skus
    ) {
        this(productCode, itemNo, productName, categoryId, brand, type, mainImageFileId, status, remark,
                specifications, skus, null, null);
    }

    public SaveProductCommand(
            String productCode,
            String itemNo,
            String productName,
            Long categoryId,
            String brand,
            ProductType type,
            Long mainImageFileId,
            String remark,
            List<Specification> specifications,
            List<SaveSkuCommand> skus
    ) {
        this(
                productCode, itemNo, productName, categoryId, brand, type, mainImageFileId,
                null, remark, specifications, skus
        );
    }
}
