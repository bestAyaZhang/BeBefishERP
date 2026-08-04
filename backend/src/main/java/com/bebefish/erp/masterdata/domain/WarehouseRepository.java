package com.bebefish.erp.masterdata.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WarehouseRepository {
    boolean existsByNumber(String number, Long excludedId);
    boolean existsByName(String name, Long excludedId);
    Warehouse save(Warehouse warehouse);
    Optional<Warehouse> findById(long id);
    Optional<Warehouse> findDefault();
    void clearDefault();
    Page<Warehouse> findAll(String keyword, String status, Pageable pageable);
}
