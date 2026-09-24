package com.bebefish.erp.shipping.api;

import java.util.List;

public record ShippingFormOptions(List<String> shopNames, List<PreparerOption> preparers) {
    public ShippingFormOptions {
        shopNames = shopNames == null ? List.of() : List.copyOf(shopNames);
        preparers = preparers == null ? List.of() : List.copyOf(preparers);
    }

    public record PreparerOption(long employeeId, String employeeName) {}
}
