package com.bebefish.erp.product.api;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.application.PackagingCommand;
import com.bebefish.erp.product.application.SaveProductCommand;
import com.bebefish.erp.product.application.SaveSkuCommand;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Specification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public record SaveProductRequest(
        @Size(max = 50) String productCode,
        @NotBlank @Size(max = 50) String itemNo,
        @NotBlank @Size(max = 200) String productName,
        @NotNull Long categoryId,
        @Size(max = 100) String brand,
        @NotBlank String productType,
        Long mainImageFileId,
        @Size(max = 500) String remark,
        List<@Valid SpecificationInput> specifications,
        List<@Valid SkuInput> skus
) {
    SaveProductCommand toCommand() {
        return new SaveProductCommand(
                productCode, itemNo, productName, categoryId, brand, parseType(productType), mainImageFileId,
                remark,
                specifications == null ? List.of() : specifications.stream().map(SpecificationInput::toDomain).toList(),
                skus == null ? List.of() : skus.stream().map(SkuInput::toCommand).toList()
        );
    }

    private ProductType parseType(String value) {
        try {
            return ProductType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "产品类型无效");
        }
    }

    public record SpecificationInput(
            @NotBlank @Size(max = 100) String name,
            List<@NotBlank @Size(max = 100) String> values
    ) {
        Specification toDomain() {
            return new Specification(name, values);
        }
    }

    public record SkuInput(
            Long id,
            @Size(max = 50) String skuCode,
            @Size(max = 100) String barcode,
            @Size(max = 200) String skuName,
            List<@Size(max = 100) String> specificationValues,
            @Size(max = 20) String salesUnit,
            BigDecimal defaultSalePrice,
            BigDecimal standardCost,
            BigDecimal safetyStockQuantity,
            BigDecimal packageLengthCm,
            BigDecimal packageWidthCm,
            BigDecimal packageHeightCm,
            BigDecimal packageVolumeCm3,
            BigDecimal innerPackageLengthCm,
            BigDecimal innerPackageWidthCm,
            BigDecimal innerPackageHeightCm,
            BigDecimal netWeightKg,
            BigDecimal grossWeightKg,
            BigDecimal gramWeightG,
            BigDecimal innerPackageWeightKg,
            @Size(max = 100) String packagingMethod,
            Integer cartonQuantity,
            Long skuImageFileId,
            Long packageImageFileId,
            Long cartonImageFileId
    ) {
        SaveSkuCommand toCommand() {
            return new SaveSkuCommand(
                    id, skuCode, barcode, skuName,
                    specificationValues == null ? List.of() : specificationValues,
                    salesUnit, defaultSalePrice, standardCost, safetyStockQuantity,
                    new PackagingCommand(
                            packageLengthCm, packageWidthCm, packageHeightCm, packageVolumeCm3,
                            innerPackageLengthCm, innerPackageWidthCm, innerPackageHeightCm,
                            netWeightKg, grossWeightKg, gramWeightG, innerPackageWeightKg,
                            packagingMethod, cartonQuantity,
                            packageImageFileId, cartonImageFileId
                    ),
                    skuImageFileId
            );
        }
    }
}
