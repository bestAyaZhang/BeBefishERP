package com.bebefish.erp.product.application;

import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.SupplierQuote;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductCompletenessCalculator {
    private static final int GROUP_SCORE = 20;

    public ProductCompleteness calculate(Product product, Map<Long, List<SupplierQuote>> quotesBySkuId) {
        var enabledSkus = product.skus().stream()
                .filter(sku -> "enabled".equals(sku.status()))
                .toList();
        var missingGroups = new ArrayList<String>();

        addMissing(missingGroups, "基本信息", basicInfoComplete(product));
        addMissing(missingGroups, "SKU 信息", skuInfoComplete(enabledSkus));
        addMissing(missingGroups, "采购信息", purchasingComplete(enabledSkus, quotesBySkuId));
        addMissing(missingGroups, "包装重量", packagingComplete(enabledSkus));
        addMissing(missingGroups, "图片资料", imagesComplete(product, enabledSkus));

        var percent = (5 - missingGroups.size()) * GROUP_SCORE;
        return new ProductCompleteness(
                percent,
                percent == 100 ? "complete" : "incomplete",
                missingGroups
        );
    }

    private boolean basicInfoComplete(Product product) {
        return hasText(product.code())
                && hasText(product.itemNo())
                && hasText(product.name())
                && product.categoryId() != null
                && product.type() != null
                && hasText(product.status());
    }

    private boolean skuInfoComplete(List<Sku> enabledSkus) {
        return !enabledSkus.isEmpty() && enabledSkus.stream().allMatch(sku ->
                hasText(sku.code())
                        && hasText(sku.name())
                        && hasText(sku.salesUnit())
                        && sku.defaultSalePrice() != null
                        && sku.standardCost() != null
                        && sku.safetyStockQuantity() != null
        );
    }

    private boolean purchasingComplete(
            List<Sku> enabledSkus,
            Map<Long, List<SupplierQuote>> quotesBySkuId
    ) {
        return !enabledSkus.isEmpty() && enabledSkus.stream().allMatch(sku -> {
            var quotes = quotesBySkuId.get(sku.id());
            return quotes != null && quotes.stream().anyMatch(quote ->
                    quote.isDefault() && "enabled".equals(quote.status())
            );
        });
    }

    private boolean packagingComplete(List<Sku> enabledSkus) {
        return !enabledSkus.isEmpty() && enabledSkus.stream().allMatch(sku -> {
            Packaging packaging = sku.packaging();
            return packaging != null
                    && packaging.lengthCm() != null
                    && packaging.widthCm() != null
                    && packaging.heightCm() != null
                    && packaging.cartonQuantity() != null
                    && packaging.netWeightKg() != null
                    && packaging.grossWeightKg() != null;
        });
    }

    private boolean imagesComplete(Product product, List<Sku> enabledSkus) {
        return product.mainImageFileId() != null
                && enabledSkus.stream().allMatch(sku -> sku.skuImageFileId() != null);
    }

    private void addMissing(List<String> missingGroups, String groupName, boolean complete) {
        if (!complete) {
            missingGroups.add(groupName);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ProductCompleteness(int percent, String status, List<String> missingGroups) {
        public ProductCompleteness {
            missingGroups = List.copyOf(missingGroups);
        }
    }
}
