package com.bebefish.erp.product.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class DefaultSkuCombinationGenerator implements SkuCombinationGenerator {
    @Override
    public List<SkuCombination> generate(
            ProductType type,
            String productCode,
            List<Specification> specifications
    ) {
        if (type == null) {
            throw new IllegalArgumentException("产品类型不能为空");
        }
        var normalizedCode = required(productCode, "产品编码不能为空");
        if (type == ProductType.SIMPLE) {
            return List.of(new SkuCombination(
                    normalizedCode + "-DEFAULT", true, List.of()
            ));
        }

        var normalizedSpecifications = normalizeSpecifications(specifications);
        var valueCombinations = cartesianProduct(normalizedSpecifications);
        var result = new ArrayList<SkuCombination>(valueCombinations.size());
        for (var index = 0; index < valueCombinations.size(); index++) {
            result.add(new SkuCombination(
                    normalizedCode + "-" + String.format("%03d", index + 1),
                    false,
                    valueCombinations.get(index)
            ));
        }
        return List.copyOf(result);
    }

    private List<Specification> normalizeSpecifications(List<Specification> specifications) {
        if (specifications == null || specifications.isEmpty()) {
            throw new IllegalArgumentException("多规格产品至少需要一个规格");
        }
        var names = new HashSet<String>();
        var normalized = new ArrayList<Specification>();
        for (var specification : specifications) {
            if (specification == null) {
                throw new IllegalArgumentException("规格不能为空");
            }
            var name = required(specification.name(), "规格名称不能为空");
            if (!names.add(name)) {
                throw new IllegalArgumentException("规格名称不能重复：" + name);
            }
            var values = new LinkedHashSet<String>();
            for (var value : specification.values()) {
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("规格至少需要一个有效值：" + name);
            }
            normalized.add(new Specification(name, List.copyOf(values)));
        }
        return List.copyOf(normalized);
    }

    private List<List<String>> cartesianProduct(List<Specification> specifications) {
        List<List<String>> combinations = List.of(List.of());
        for (var specification : specifications) {
            var expanded = new ArrayList<List<String>>();
            for (var combination : combinations) {
                for (var value : specification.values()) {
                    var next = new ArrayList<>(combination);
                    next.add(value);
                    expanded.add(List.copyOf(next));
                }
            }
            combinations = expanded;
        }
        return combinations;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
