package com.bebefish.erp.sales.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SalesOrderRepository {
    SalesOrder save(SalesOrder order);

    Optional<SalesOrder> findById(long id);

    Page<SalesOrder> findAll(SalesOrderSearchCriteria criteria, Pageable pageable);

    void deleteById(long id);
}
