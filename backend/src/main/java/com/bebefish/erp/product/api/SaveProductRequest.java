package com.bebefish.erp.product.api;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.application.PackagingCommand;
import com.bebefish.erp.product.application.ProductSupplierQuoteCommand;
import com.bebefish.erp.product.application.SaveProductCommand;
import com.bebefish.erp.product.application.SaveSkuCommand;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Specification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        @Size(max = 20) String status,
        @Size(max = 500) String remark,
        List<@Valid SpecificationInput> specifications,
        List<@Valid SkuInput> skus
) {
    SaveProductCommand toCommand() {
        return new SaveProductCommand(
                productCode, itemNo, productName, categoryId, brand, parseType(productType), mainImageFileId,
                status, remark,
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
            @DecimalMin(value = "0", message = "默认售价不能小于 0")
            @Digits(integer = 15, fraction = 4, message = "默认售价最多允许 15 位整数和 4 位小数")
            BigDecimal defaultSalePrice,
            @DecimalMin(value = "0", message = "标准成本不能小于 0")
            @Digits(integer = 15, fraction = 4, message = "标准成本最多允许 15 位整数和 4 位小数")
            BigDecimal standardCost,
            @DecimalMin(value = "0", message = "安全库存不能小于 0")
            @Digits(integer = 14, fraction = 4, message = "安全库存最多允许 14 位整数和 4 位小数")
            BigDecimal safetyStockQuantity,
            @DecimalMin(value = "0", message = "包装长不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "包装长最多允许 9 位整数和 3 位小数")
            BigDecimal packageLengthCm,
            @DecimalMin(value = "0", message = "包装宽不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "包装宽最多允许 9 位整数和 3 位小数")
            BigDecimal packageWidthCm,
            @DecimalMin(value = "0", message = "包装高不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "包装高最多允许 9 位整数和 3 位小数")
            BigDecimal packageHeightCm,
            @DecimalMin(value = "0", message = "包装体积不能小于 0")
            @Digits(integer = 15, fraction = 3, message = "包装体积最多允许 15 位整数和 3 位小数")
            BigDecimal packageVolumeCm3,
            @DecimalMin(value = "0", message = "内盒长不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "内盒长最多允许 9 位整数和 3 位小数")
            BigDecimal innerPackageLengthCm,
            @DecimalMin(value = "0", message = "内盒宽不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "内盒宽最多允许 9 位整数和 3 位小数")
            BigDecimal innerPackageWidthCm,
            @DecimalMin(value = "0", message = "内盒高不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "内盒高最多允许 9 位整数和 3 位小数")
            BigDecimal innerPackageHeightCm,
            @DecimalMin(value = "0", message = "净重不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "净重最多允许 9 位整数和 3 位小数")
            BigDecimal netWeightKg,
            @DecimalMin(value = "0", message = "毛重不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "毛重最多允许 9 位整数和 3 位小数")
            BigDecimal grossWeightKg,
            @DecimalMin(value = "0", message = "克重不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "克重最多允许 9 位整数和 3 位小数")
            BigDecimal gramWeightG,
            @DecimalMin(value = "0", message = "内盒重量不能小于 0")
            @Digits(integer = 9, fraction = 3, message = "内盒重量最多允许 9 位整数和 3 位小数")
            BigDecimal innerPackageWeightKg,
            @Size(max = 100) String packagingMethod,
            @Positive(message = "装箱数必须大于 0") Integer cartonQuantity,
            Long skuImageFileId,
            Long packageImageFileId,
            Long cartonImageFileId,
            Boolean defaultSku,
            @Size(max = 20) String status,
            List<@NotNull @Valid SupplierQuoteInput> supplierQuotes
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
                    skuImageFileId,
                    defaultSku,
                    status,
                    supplierQuotes == null
                            ? null
                            : supplierQuotes.stream().map(SupplierQuoteInput::toCommand).toList()
            );
        }
    }

    public record SupplierQuoteInput(
            Long id,
            Long supplierId,
            @Size(max = 100) String supplierItemNo,
            @NotNull @DecimalMin(value = "0", message = "采购价不能小于 0")
            @Digits(integer = 15, fraction = 4, message = "采购价最多允许 15 位整数和 4 位小数")
            BigDecimal purchasePrice,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "最小采购量必须大于 0")
            @Digits(integer = 15, fraction = 4, message = "最小采购量最多允许 15 位整数和 4 位小数")
            BigDecimal minPurchaseQuantity,
            boolean defaultQuote,
            @Size(max = 20) String status
    ) {
        ProductSupplierQuoteCommand toCommand() {
            return new ProductSupplierQuoteCommand(
                    id, supplierId, supplierItemNo, purchasePrice, minPurchaseQuantity, defaultQuote, status
            );
        }
    }
}
