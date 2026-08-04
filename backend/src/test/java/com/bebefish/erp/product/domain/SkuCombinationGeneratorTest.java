package com.bebefish.erp.product.domain;

import static com.bebefish.erp.product.domain.ProductType.SIMPLE;
import static com.bebefish.erp.product.domain.ProductType.VARIANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkuCombinationGeneratorTest {
    private SkuCombinationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DefaultSkuCombinationGenerator();
    }

    @Test
    void simpleProductCreatesOneDefaultSku() {
        var result = generator.generate(SIMPLE, "P100", List.of());

        assertThat(result).containsExactly(
                new SkuCombination("P100-DEFAULT", true, List.of())
        );
    }

    @Test
    void variantProductCreatesCartesianProductInInputOrder() {
        var result = generator.generate(VARIANT, "P200", List.of(
                specification("颜色", "透明", "烟灰"),
                specification("花纹", "竖纹", "樱花纹")
        ));

        assertThat(result).extracting(SkuCombination::skuCode)
                .containsExactly("P200-001", "P200-002", "P200-003", "P200-004");
        assertThat(result).extracting(SkuCombination::displayText)
                .containsExactly("透明 / 竖纹", "透明 / 樱花纹", "烟灰 / 竖纹", "烟灰 / 樱花纹");
        assertThat(result).noneMatch(SkuCombination::isDefault);
    }

    @Test
    void variantProductTrimsAndDeduplicatesValuesByFirstOccurrence() {
        var result = generator.generate(VARIANT, " P300 ", List.of(
                specification("颜色", "透明", " 透明 ", "", "烟灰")
        ));

        assertThat(result).containsExactly(
                new SkuCombination("P300-001", false, List.of("透明")),
                new SkuCombination("P300-002", false, List.of("烟灰"))
        );
    }

    @Test
    void variantProductRequiresAtLeastOneSpecification() {
        assertThatThrownBy(() -> generator.generate(VARIANT, "P400", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("多规格产品至少需要一个规格");
    }

    @Test
    void variantProductRejectsSpecificationWithoutEffectiveValues() {
        assertThatThrownBy(() -> generator.generate(VARIANT, "P400", List.of(
                specification("颜色", "", " ")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("规格至少需要一个有效值：颜色");
    }

    @Test
    void variantProductRejectsDuplicateSpecificationNames() {
        assertThatThrownBy(() -> generator.generate(VARIANT, "P400", List.of(
                specification("颜色", "透明"),
                specification(" 颜色 ", "烟灰")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("规格名称不能重复：颜色");
    }

    private Specification specification(String name, String... values) {
        return new Specification(name, List.of(values));
    }
}
