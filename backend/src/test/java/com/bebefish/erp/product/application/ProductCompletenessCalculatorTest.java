package com.bebefish.erp.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.SupplierQuote;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductCompletenessCalculatorTest {
    private final ProductCompletenessCalculator calculator = new ProductCompletenessCalculator();

    @Test
    void scoresFiveEqualCompletenessGroups() {
        var incomplete = calculator.calculate(productWithoutImagesOrQuotes(), Map.of());

        assertThat(incomplete.percent()).isEqualTo(40);
        assertThat(incomplete.status()).isEqualTo("incomplete");
        assertThat(incomplete.missingGroups()).containsExactly("采购信息", "包装重量", "图片资料");

        var complete = calculator.calculate(completeProduct(), completeQuotes());

        assertThat(complete.percent()).isEqualTo(100);
        assertThat(complete.status()).isEqualTo("complete");
        assertThat(complete.missingGroups()).isEmpty();
    }

    @Test
    void reportsMissingGroupsInTheirFixedOrder() {
        var product = new Product(
                1L, "", "", "", null, null, null,
                null, "enabled", null, List.of(), List.of(), null, null
        );

        var result = calculator.calculate(product, Map.of());

        assertThat(result.percent()).isZero();
        assertThat(result.status()).isEqualTo("incomplete");
        assertThat(result.missingGroups()).containsExactly(
                "基本信息", "SKU 信息", "采购信息", "包装重量", "图片资料"
        );
    }

    @Test
    void requiresEveryEnabledSkuToHaveCoreFieldsAndIgnoresDisabledSkus() {
        var complete = completeProduct();
        var disabledIncompleteSku = new Sku(
                12L, null, null, null, null, List.of(), null,
                null, null, null, null, null, false, "disabled"
        );
        var withDisabledSku = productWithSkus(complete, List.of(complete.skus().getFirst(), disabledIncompleteSku));

        assertThat(calculator.calculate(withDisabledSku, completeQuotes()).percent()).isEqualTo(100);

        var enabledIncompleteSku = new Sku(
                11L, "SKU-001", null, "测试 SKU", null, List.of(), "",
                new BigDecimal("9.90"), new BigDecimal("2.20"), new BigDecimal("5"),
                completePackaging(), 2L, true, "enabled"
        );
        var withIncompleteEnabledSku = productWithSkus(complete, List.of(enabledIncompleteSku));

        assertThat(calculator.calculate(withIncompleteEnabledSku, completeQuotes()).missingGroups())
                .containsExactly("SKU 信息");
    }

    @Test
    void requiresAnEnabledDefaultSupplierQuoteForEveryEnabledSku() {
        var product = completeProduct();
        var disabledDefault = quote(true, "disabled");
        var enabledNonDefault = quote(false, "enabled");

        assertThat(calculator.calculate(product, Map.of(11L, List.of(disabledDefault))).missingGroups())
                .containsExactly("采购信息");
        assertThat(calculator.calculate(product, Map.of(11L, List.of(enabledNonDefault))).missingGroups())
                .containsExactly("采购信息");
        assertThat(calculator.calculate(product, completeQuotes()).missingGroups()).isEmpty();
    }

    @Test
    void requiresAnEnabledSkuForImageCompleteness() {
        var complete = completeProduct();
        var enabledSku = complete.skus().getFirst();
        var disabledSku = new Sku(
                enabledSku.id(), enabledSku.code(), enabledSku.barcode(), enabledSku.name(), enabledSku.specText(),
                enabledSku.specificationValues(), enabledSku.salesUnit(), enabledSku.defaultSalePrice(),
                enabledSku.standardCost(), enabledSku.safetyStockQuantity(), enabledSku.packaging(),
                enabledSku.skuImageFileId(), enabledSku.isDefault(), "disabled"
        );
        var product = productWithSkus(complete, List.of(disabledSku));

        var result = calculator.calculate(product, Map.of());

        assertThat(result.percent()).isEqualTo(20);
        assertThat(result.status()).isEqualTo("incomplete");
        assertThat(result.missingGroups()).containsExactly(
                "SKU 信息", "采购信息", "包装重量", "图片资料"
        );
    }

    private Product productWithoutImagesOrQuotes() {
        var complete = completeProduct();
        var sku = complete.skus().getFirst();
        var incompleteSku = new Sku(
                sku.id(), sku.code(), sku.barcode(), sku.name(), sku.specText(), sku.specificationValues(),
                sku.salesUnit(), sku.defaultSalePrice(), sku.standardCost(), sku.safetyStockQuantity(),
                null, null, sku.isDefault(), sku.status()
        );
        return new Product(
                complete.id(), complete.code(), complete.itemNo(), complete.name(), complete.categoryId(),
                complete.brand(), complete.type(), null, complete.status(), complete.remark(),
                complete.specifications(), List.of(incompleteSku), complete.createdAt(), complete.updatedAt()
        );
    }

    private Product completeProduct() {
        var sku = new Sku(
                11L, "SKU-001", null, "测试 SKU", null, List.of(), "件",
                new BigDecimal("9.90"), new BigDecimal("2.20"), new BigDecimal("5"),
                completePackaging(), 2L, true, "enabled"
        );
        return new Product(
                1L, "PRD-001", "ITEM-001", "测试商品", 3L, "测试品牌", ProductType.SIMPLE,
                1L, "enabled", null, List.of(), List.of(sku), null, null
        );
    }

    private Packaging completePackaging() {
        return new Packaging(
                new BigDecimal("42"), new BigDecimal("31"), new BigDecimal("28"),
                new BigDecimal("36456"), null, null, null,
                new BigDecimal("8.5"), new BigDecimal("9.2"), null, null,
                null, 12, null, null
        );
    }

    private Map<Long, List<SupplierQuote>> completeQuotes() {
        return Map.of(11L, List.of(quote(true, "enabled")));
    }

    private SupplierQuote quote(boolean defaultQuote, String status) {
        return new SupplierQuote(
                21L, 11L, 31L, "SUP-001", new BigDecimal("4.20"),
                BigDecimal.ONE, defaultQuote, status
        );
    }

    private Product productWithSkus(Product product, List<Sku> skus) {
        return new Product(
                product.id(), product.code(), product.itemNo(), product.name(), product.categoryId(),
                product.brand(), product.type(), product.mainImageFileId(), product.status(), product.remark(),
                product.specifications(), new ArrayList<>(skus), product.createdAt(), product.updatedAt()
        );
    }
}
