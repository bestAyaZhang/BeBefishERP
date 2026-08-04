package com.bebefish.erp.product.application;

import com.bebefish.erp.product.domain.ProductType;
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
        String remark,
        List<Specification> specifications,
        List<SaveSkuCommand> skus
) {
    public SaveProductCommand {
        specifications = specifications == null ? List.of() : List.copyOf(specifications);
        skus = skus == null ? List.of() : List.copyOf(skus);
    }
}
