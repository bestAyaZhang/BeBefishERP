package com.bebefish.erp.shipping.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.shipping")
public record ShippingProperties(List<String> shopNames) {
    public ShippingProperties {
        shopNames = shopNames == null ? List.of() : List.copyOf(shopNames);
    }
}
