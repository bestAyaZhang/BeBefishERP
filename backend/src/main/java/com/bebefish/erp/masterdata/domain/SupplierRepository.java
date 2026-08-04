package com.bebefish.erp.masterdata.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupplierRepository {
    boolean existsByNumber(String number, Long excludedId);
    Supplier save(Supplier supplier);
    Optional<Supplier> findById(long id);
    Page<Supplier> findAll(String keyword, String status, Pageable pageable);
}
