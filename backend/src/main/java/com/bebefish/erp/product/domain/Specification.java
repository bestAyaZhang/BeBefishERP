package com.bebefish.erp.product.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Specification(String name, List<String> values) {
    public Specification {
        values = values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
