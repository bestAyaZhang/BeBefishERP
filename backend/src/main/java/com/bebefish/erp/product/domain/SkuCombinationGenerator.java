package com.bebefish.erp.product.domain;

import java.util.List;

public interface SkuCombinationGenerator {
    List<SkuCombination> generate(
            ProductType type,
            String productCode,
            List<Specification> specifications
    );
}
