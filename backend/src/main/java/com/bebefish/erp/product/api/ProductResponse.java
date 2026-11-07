package com.bebefish.erp.product.api;

import com.bebefish.erp.product.application.ProductCatalogMetrics;
import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductImagePack;
import com.bebefish.erp.product.domain.ProductRepository.ImageFile;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.Specification;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProductResponse(
        Long id,
        String productCode,
        String itemNo,
        String productName,
        Long categoryId,
        String categoryName,
        String brand,
        String productType,
        Long mainImageFileId,
        String mainImageUrl,
        String defaultSupplierName,
        BigDecimal totalStock,
        BigDecimal totalSafetyStock,
        BigDecimal defaultSalePrice,
        int completenessPercent,
        String completenessStatus,
        List<String> missingGroups,
        String status,
        String remark,
        List<SpecificationResponse> specifications,
        List<SkuResponse> skus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ImagePackResponse> imagePacks,
        List<ImageResponse> materialImages
) {
    public ProductResponse(
            Long id, String productCode, String itemNo, String productName, Long categoryId, String categoryName,
            String brand, String productType, Long mainImageFileId, String mainImageUrl, String defaultSupplierName,
            BigDecimal totalStock, BigDecimal totalSafetyStock, BigDecimal defaultSalePrice, int completenessPercent,
            String completenessStatus, List<String> missingGroups, String status, String remark,
            List<SpecificationResponse> specifications, List<SkuResponse> skus,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        this(id, productCode, itemNo, productName, categoryId, categoryName, brand, productType, mainImageFileId,
                mainImageUrl, defaultSupplierName, totalStock, totalSafetyStock, defaultSalePrice, completenessPercent,
                completenessStatus, missingGroups, status, remark, specifications, skus, createdAt, updatedAt, null, List.of());
    }

    static ProductResponse from(
            Product product,
            ProductCatalogMetrics metrics,
            Map<Long, String> resolvedImageUrls
    ) {
        return new ProductResponse(
                product.id(), product.code(), product.itemNo(), product.name(), product.categoryId(),
                metrics.categoryName(), product.brand(), product.type().name().toLowerCase(), product.mainImageFileId(),
                resolvedImageUrls.get(product.mainImageFileId()), metrics.defaultSupplierName(),
                metrics.totalStock(), metrics.totalSafetyStock(), metrics.defaultSalePrice(),
                metrics.completeness().percent(), metrics.completeness().status(),
                metrics.completeness().missingGroups(), product.status(), product.remark(),
                product.specifications().stream().map(SpecificationResponse::from).toList(),
                product.skus().stream().map(sku -> SkuResponse.from(
                        sku, metrics.skus().get(sku.id()), resolvedImageUrls
                )).toList(),
                product.createdAt(), product.updatedAt(),
                product.imagePacks() == null ? null : product.imagePacks().stream()
                        .map(pack -> ImagePackResponse.from(pack, resolvedImageUrls, metrics.imageFiles())).toList(),
                images(product.materialImages(), resolvedImageUrls, metrics.imageFiles())
        );
    }

    private static List<ImageResponse> images(List<Long> fileIds, Map<Long, String> urls, Map<Long, ImageFile> files) {
        if (fileIds == null) {
            return List.of();
        }
        return fileIds.stream().map(fileId -> {
            var file = files.get(fileId);
            return new ImageResponse(fileId, urls.get(fileId), file == null ? null : file.name());
        }).toList();
    }

    public record ImageResponse(Long fileId, String url, String name) {
    }

    public record ImagePackResponse(
            String name,
            List<ImageResponse> main,
            List<ImageResponse> sku,
            List<ImageResponse> detail,
            @JsonProperty("long") List<ImageResponse> longImages
    ) {
        static ImagePackResponse from(ProductImagePack pack, Map<Long, String> urls, Map<Long, ImageFile> files) {
            return new ImagePackResponse(pack.name(), images(pack.main(), urls, files), images(pack.sku(), urls, files),
                    images(pack.detail(), urls, files), images(pack.longImages(), urls, files));
        }
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
            BigDecimal safetyStockQuantity,
            BigDecimal stockQuantity,
            BigDecimal packageLengthCm,
            BigDecimal packageWidthCm,
            BigDecimal packageHeightCm,
            BigDecimal packageVolumeCm3,
            BigDecimal innerPackageLengthCm,
            BigDecimal innerPackageWidthCm,
            BigDecimal innerPackageHeightCm,
            BigDecimal productLengthCm,
            BigDecimal productWidthCm,
            BigDecimal productHeightCm,
            BigDecimal capacityMl,
            BigDecimal netWeightKg,
            BigDecimal grossWeightKg,
            BigDecimal gramWeightG,
            BigDecimal innerPackageWeightKg,
            String packagingMethod,
            Integer cartonQuantity,
            Long skuImageFileId,
            String skuImageUrl,
            Long packageImageFileId,
            String packageImageUrl,
            Long cartonImageFileId,
            String cartonImageUrl,
            List<CatalogSupplierQuoteResponse> supplierQuotes,
            boolean defaultSku,
            String status,
            BigDecimal productMouthDiameterCm,
            Long productPhotoFileId,
            String productPhotoUrl
    ) {
        public SkuResponse(
                Long id,
                String skuCode,
                String barcode,
                String skuName,
                String specText,
                List<String> specificationValues,
                String salesUnit,
                BigDecimal defaultSalePrice,
                BigDecimal standardCost,
                BigDecimal safetyStockQuantity,
                BigDecimal stockQuantity,
                BigDecimal packageLengthCm,
                BigDecimal packageWidthCm,
                BigDecimal packageHeightCm,
                BigDecimal packageVolumeCm3,
                BigDecimal innerPackageLengthCm,
                BigDecimal innerPackageWidthCm,
                BigDecimal innerPackageHeightCm,
                BigDecimal productLengthCm,
                BigDecimal productWidthCm,
                BigDecimal productHeightCm,
                BigDecimal capacityMl,
                BigDecimal netWeightKg,
                BigDecimal grossWeightKg,
                BigDecimal gramWeightG,
                BigDecimal innerPackageWeightKg,
                String packagingMethod,
                Integer cartonQuantity,
                Long skuImageFileId,
                String skuImageUrl,
                Long packageImageFileId,
                String packageImageUrl,
                Long cartonImageFileId,
                String cartonImageUrl,
                List<CatalogSupplierQuoteResponse> supplierQuotes,
                boolean defaultSku,
                String status
        ) {
            this(
                    id, skuCode, barcode, skuName, specText,
                    specificationValues, salesUnit, defaultSalePrice, standardCost, safetyStockQuantity,
                    stockQuantity, packageLengthCm, packageWidthCm, packageHeightCm, packageVolumeCm3,
                    innerPackageLengthCm, innerPackageWidthCm, innerPackageHeightCm, productLengthCm, productWidthCm,
                    productHeightCm, capacityMl, netWeightKg, grossWeightKg, gramWeightG,
                    innerPackageWeightKg, packagingMethod, cartonQuantity, skuImageFileId, skuImageUrl,
                    packageImageFileId, packageImageUrl, cartonImageFileId, cartonImageUrl, supplierQuotes,
                    defaultSku, status, null, null, null
            );
        }

        public SkuResponse(
                Long id, String skuCode, String barcode, String skuName, String specText, List<String> specificationValues,
                String salesUnit, BigDecimal defaultSalePrice, BigDecimal standardCost, BigDecimal safetyStockQuantity,
                BigDecimal stockQuantity, BigDecimal packageLengthCm, BigDecimal packageWidthCm, BigDecimal packageHeightCm,
                BigDecimal packageVolumeCm3, BigDecimal innerPackageLengthCm, BigDecimal innerPackageWidthCm,
                BigDecimal innerPackageHeightCm, BigDecimal productLengthCm, BigDecimal productWidthCm,
                BigDecimal productHeightCm, BigDecimal capacityMl, BigDecimal netWeightKg, BigDecimal grossWeightKg,
                BigDecimal gramWeightG, BigDecimal innerPackageWeightKg, String packagingMethod, Integer cartonQuantity,
                Long skuImageFileId, String skuImageUrl, Long packageImageFileId, String packageImageUrl,
                Long cartonImageFileId, String cartonImageUrl, List<CatalogSupplierQuoteResponse> supplierQuotes,
                boolean defaultSku, String status, BigDecimal productMouthDiameterCm
        ) {
            this(id, skuCode, barcode, skuName, specText, specificationValues, salesUnit, defaultSalePrice, standardCost,
                    safetyStockQuantity, stockQuantity, packageLengthCm, packageWidthCm, packageHeightCm, packageVolumeCm3,
                    innerPackageLengthCm, innerPackageWidthCm, innerPackageHeightCm, productLengthCm, productWidthCm,
                    productHeightCm, capacityMl, netWeightKg, grossWeightKg, gramWeightG, innerPackageWeightKg,
                    packagingMethod, cartonQuantity, skuImageFileId, skuImageUrl, packageImageFileId, packageImageUrl,
                    cartonImageFileId, cartonImageUrl, supplierQuotes, defaultSku, status, productMouthDiameterCm, null, null);
        }

        static SkuResponse from(
                Sku sku,
                ProductCatalogMetrics.SkuCatalogMetrics metrics,
                Map<Long, String> imageUrls
        ) {
            Packaging packaging = sku.packaging();
            return new SkuResponse(
                    sku.id(), sku.code(), sku.barcode(), sku.name(), sku.specText(),
                    sku.specificationValues(), sku.salesUnit(), sku.defaultSalePrice(), sku.standardCost(),
                    sku.safetyStockQuantity(), metrics.stockQuantity(),
                    packaging == null ? null : packaging.lengthCm(),
                    packaging == null ? null : packaging.widthCm(),
                    packaging == null ? null : packaging.heightCm(),
                    packaging == null ? null : packaging.volumeCm3(),
                    packaging == null ? null : packaging.innerLengthCm(),
                    packaging == null ? null : packaging.innerWidthCm(),
                    packaging == null ? null : packaging.innerHeightCm(),
                    packaging == null ? null : packaging.productLengthCm(),
                    packaging == null ? null : packaging.productWidthCm(),
                    packaging == null ? null : packaging.productHeightCm(),
                    packaging == null ? null : packaging.capacityMl(),
                    packaging == null ? null : packaging.netWeightKg(),
                    packaging == null ? null : packaging.grossWeightKg(),
                    packaging == null ? null : packaging.gramWeightG(),
                    packaging == null ? null : packaging.innerWeightKg(),
                    packaging == null ? null : packaging.method(),
                    packaging == null ? null : packaging.cartonQuantity(),
                    sku.skuImageFileId(),
                    imageUrls.get(sku.skuImageFileId()),
                    packaging == null ? null : packaging.packageImageFileId(),
                    packaging == null ? null : imageUrls.get(packaging.packageImageFileId()),
                    packaging == null ? null : packaging.cartonImageFileId(),
                    packaging == null ? null : imageUrls.get(packaging.cartonImageFileId()),
                    metrics.supplierQuotes().stream().map(CatalogSupplierQuoteResponse::from).toList(),
                    sku.isDefault(), sku.status(),
                    packaging == null ? null : packaging.productMouthDiameterCm(),
                    sku.productPhotoFileId(), imageUrls.get(sku.productPhotoFileId())
            );
        }
    }

    public record CatalogSupplierQuoteResponse(
            Long id,
            Long skuId,
            Long supplierId,
            String supplierName,
            String supplierItemNo,
            BigDecimal purchasePrice,
            BigDecimal minPurchaseQuantity,
            boolean defaultQuote,
            String status
    ) {
        static CatalogSupplierQuoteResponse from(
                ProductCatalogMetrics.CatalogSupplierQuote catalogQuote
        ) {
            var quote = catalogQuote.quote();
            return new CatalogSupplierQuoteResponse(
                    quote.id(), quote.skuId(), quote.supplierId(), catalogQuote.supplierName(),
                    quote.supplierItemNo(), quote.purchasePrice(), quote.minPurchaseQuantity(),
                    quote.isDefault(), quote.status()
            );
        }
    }
}
