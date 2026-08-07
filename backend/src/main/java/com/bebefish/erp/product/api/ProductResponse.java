package com.bebefish.erp.product.api;

import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.Specification;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ProductResponse(
        Long id,
        String productCode,
        String itemNo,
        String productName,
        Long categoryId,
        String brand,
        String productType,
        Long mainImageFileId,
        String mainImageUrl,
        String defaultSupplierName,
        String status,
        String remark,
        List<SpecificationResponse> specifications,
        List<SkuResponse> skus
) {
    static ProductResponse from(Product product) {
        return from(product, (String) null, null);
    }

    static ProductResponse from(Product product, String mainImageUrl, String defaultSupplierName) {
        var imageUrls = new HashMap<Long, String>();
        if (product.mainImageFileId() != null && mainImageUrl != null) {
            imageUrls.put(product.mainImageFileId(), mainImageUrl);
        }
        return from(product, imageUrls, defaultSupplierName);
    }

    static ProductResponse from(Product product, Map<Long, String> imageUrls, String defaultSupplierName) {
        return new ProductResponse(
                product.id(), product.code(), product.itemNo(), product.name(), product.categoryId(),
                product.brand(), product.type().name().toLowerCase(), product.mainImageFileId(),
                imageUrls.get(product.mainImageFileId()), defaultSupplierName, product.status(), product.remark(),
                product.specifications().stream().map(SpecificationResponse::from).toList(),
                product.skus().stream().map(sku -> SkuResponse.from(sku, imageUrls)).toList()
        );
    }

    public record SpecificationResponse(String name, List<String> values) {
        static SpecificationResponse from(Specification specification) {
            return new SpecificationResponse(specification.name(), specification.values());
        }
    }

    public record SkuResponse(
            Long id,
            String skuCode,
            String barcode,
            String skuName,
            String specText,
            List<String> specificationValues,
            String salesUnit,
            BigDecimal defaultSalePrice,
            BigDecimal standardCost,
            BigDecimal packageLengthCm,
            BigDecimal packageWidthCm,
            BigDecimal packageHeightCm,
            BigDecimal packageVolumeCm3,
            BigDecimal netWeightKg,
            BigDecimal grossWeightKg,
            BigDecimal gramWeightG,
            String packagingMethod,
            Integer cartonQuantity,
            Long skuImageFileId,
            String skuImageUrl,
            Long packageImageFileId,
            String packageImageUrl,
            Long cartonImageFileId,
            String cartonImageUrl,
            boolean defaultSku,
            String status
    ) {
        static SkuResponse from(Sku sku) {
            return from(sku, Map.of());
        }

        static SkuResponse from(Sku sku, Map<Long, String> imageUrls) {
            Packaging packaging = sku.packaging();
            return new SkuResponse(
                    sku.id(), sku.code(), sku.barcode(), sku.name(), sku.specText(),
                    sku.specificationValues(), sku.salesUnit(), sku.defaultSalePrice(), sku.standardCost(),
                    packaging == null ? null : packaging.lengthCm(),
                    packaging == null ? null : packaging.widthCm(),
                    packaging == null ? null : packaging.heightCm(),
                    packaging == null ? null : packaging.volumeCm3(),
                    packaging == null ? null : packaging.netWeightKg(),
                    packaging == null ? null : packaging.grossWeightKg(),
                    packaging == null ? null : packaging.gramWeightG(),
                    packaging == null ? null : packaging.method(),
                    packaging == null ? null : packaging.cartonQuantity(),
                    sku.skuImageFileId(),
                    imageUrls.get(sku.skuImageFileId()),
                    packaging == null ? null : packaging.packageImageFileId(),
                    packaging == null ? null : imageUrls.get(packaging.packageImageFileId()),
                    packaging == null ? null : packaging.cartonImageFileId(),
                    packaging == null ? null : imageUrls.get(packaging.cartonImageFileId()),
                    sku.isDefault(), sku.status()
            );
        }
    }
}
