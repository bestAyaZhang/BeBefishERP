package com.bebefish.erp.product.domain;

import java.util.List;
import java.util.Optional;

public interface SupplierQuoteRepository {
    boolean existsBySkuIdAndSupplierId(long skuId, long supplierId, Long excludedQuoteId);

    SupplierQuote save(SupplierQuote quote);

    Optional<SupplierQuote> findById(long id);

    List<SupplierQuote> findBySkuId(long skuId);

    void clearDefault(long skuId);
}
