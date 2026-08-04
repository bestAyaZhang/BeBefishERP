package com.bebefish.erp.product.domain;

import java.util.List;

public record SkuCombination(
        String skuCode,
        boolean isDefault,
        List<String> values
) {
    public SkuCombination {
        values = List.copyOf(values);
    }

    public String displayText() {
        return String.join(" / ", values);
    }
}
