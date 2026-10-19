package com.bebefish.erp.masterdata.domain;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerPriceRepository {
    Optional<BigDecimal> findPrice(long customerId, long skuId);
}
