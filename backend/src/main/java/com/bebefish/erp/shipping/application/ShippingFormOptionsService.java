package com.bebefish.erp.shipping.application;

import com.bebefish.erp.shipping.api.ShippingFormOptions;
import com.bebefish.erp.shipping.infrastructure.JdbcShippingFormOptionsRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingFormOptionsService {
    private final JdbcShippingFormOptionsRepository repository;
    private final ShippingProperties properties;

    public ShippingFormOptionsService(JdbcShippingFormOptionsRepository repository, ShippingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ShippingFormOptions options() {
        var shops = new LinkedHashSet<String>();
        for (var value : properties.shopNames()) {
            if (value != null && !value.isBlank()) shops.add(value.strip());
        }
        return new ShippingFormOptions(List.copyOf(shops), repository.findActivePreparers());
    }
}
